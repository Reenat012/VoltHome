package ru.mugalimov.volthome.data.sync

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.ProjectDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.ProjectEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.data.remote.dto.*
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class SyncManager @Inject constructor(
    private val appDb: AppDatabase,
    private val projectDao: ProjectDao,
    private val roomDao: RoomDao,
    private val groupDao: GroupDao,
    private val deviceDao: DeviceDao,
    private val projectsApi: ProjectsApi,
    private val activeProjectDataStore: ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
) {
    private val uuid = UuidMapStore { appDb }

    // Только per-project лок — без глобального «воротника»
    private val projectLocks = ConcurrentHashMap<String, Mutex>()
    private fun lockFor(projectId: String) = projectLocks.getOrPut(projectId) { Mutex() }

    suspend fun syncActiveProject() = withContext(Dispatchers.IO) {
        val pid = activeProjectDataStore.activeProjectId.firstOrNull() ?: return@withContext
        syncProject(pid)
    }

    suspend fun syncProject(projectId: String) = withContext(Dispatchers.IO) {
        lockFor(projectId).withLock {
            // Берём состояние проекта (может не быть в БД при самом первом запуске)
            val project = projectDao.getById(projectId) ?: return@withLock

            // Пробуем понять «первый ли это запуск» по наличию данных в дочерних таблицах
            val roomsCount = roomDao.countByProjectId(projectId)
            val groupsCount = groupDao.countByProjectId(projectId)
            val devicesCount = deviceDao.countByProjectId(projectId)
            val isFirstLoad = (roomsCount + groupsCount + devicesCount) == 0

            android.util.Log.i(
                "Sync",
                "Sync[project=$projectId] stage=${if (isFirstLoad) "snapshot" else "delta"}"
            )

            if (isFirstLoad) {
                // 1) СНАЧАЛА сеть (вне транзакции)
                val tree = projectsApi.getProjectTree(projectId)

                // 2) ОДНА короткая транзакция — применяем снапшот
                val toMapRooms = mutableListOf<Pair<String, Long>>()
                val toMapGroups = mutableListOf<Pair<String, Long>>()
                val toMapDevices = mutableListOf<Pair<String, Long>>()

                appDb.withTransaction {
                    applySnapshotTx(projectId, tree, toMapRooms, toMapGroups, toMapDevices)
                    // продвигаем проект до версии снапшота
                    projectDao.upsert(
                        ProjectEntity(
                            id = project.id,
                            name = project.name,
                            note = project.note,
                            version = tree.project.version,
                            updated_at = tree.project.updated_at,
                            is_deleted = tree.project.is_deleted
                        )
                    )
                }

                // 3) Записываем UUID-карты уже ПОСЛЕ транзакции, чтоб не держать connect
                putUuidMappings(toMapRooms, toMapGroups, toMapDevices)
            } else {
                // 1) СНАЧАЛА сеть (вне транзакции)
                val since = project.updated_at.ifBlank { "1970-01-01T00:00:00Z" }
                val delta = projectsApi.getDelta(projectId, since)

                // 2) Короткая транзакция — применяем дельту
                val toMapRooms = mutableListOf<Pair<String, Long>>()
                val toMapGroups = mutableListOf<Pair<String, Long>>()
                val toMapDevices = mutableListOf<Pair<String, Long>>()

                appDb.withTransaction {
                    applyRoomsDeltaTx(projectId, delta, toMapRooms)
                    applyGroupsDeltaTx(projectId, delta, toMapGroups)
                    applyDevicesDeltaTx(projectId, delta, toMapDevices)

                    // продвигаем updated_at вперёд (проекты часто отдают время сервера)
                    projectDao.upsert(
                        ProjectEntity(
                            id = project.id,
                            name = project.name,
                            note = project.note,
                            // нет delta.baseVersion → оставляем как есть
                            version = project.version,
                            updated_at = isoNow(),
                            is_deleted = project.is_deleted
                        )
                    )
                }

                // 3) UUID-карты после транзакции
                putUuidMappings(toMapRooms, toMapGroups, toMapDevices)

                android.util.Log.i(
                    "Sync",
                    "Sync[project=$projectId] pull rooms.upsert=${delta.rooms.upsert.size}, rooms.delete=${delta.rooms.delete.size}, " +
                            "groups.upsert=${delta.groups.upsert.size}, groups.delete=${delta.groups.delete.size}, " +
                            "devices.upsert=${delta.devices.upsert.size}, devices.delete=${delta.devices.delete.size}"
                )
            }

            // 4) PUSH новых (без server UUID). Сеть — вне транзакции.
            val batch = buildBatchNewEntities(projectId)
            if (hasOps(batch)) {
                projectsApi.applyBatch(projectId, batch)
            }
        }
    }

    // -------- SNAPSHOT (только то, что идёт ВНУТРИ транзакции) --------
    private suspend fun applySnapshotTx(
        projectId: String,
        tree: ProjectTreeDto,
        toMapRooms: MutableList<Pair<String, Long>>,
        toMapGroups: MutableList<Pair<String, Long>>,
        toMapDevices: MutableList<Pair<String, Long>>
    ) {
        // ROOMS
        for (r in tree.rooms) {
            val createdAt = parseDate(r.meta?.get("created_at_iso") as? String)
            val roomType = (r.meta?.get("room_type") as? String)?.let { safeRoomType(it) } ?: RoomType.STANDARD

            val existingId = roomDao.findIdByProjectAndName(projectId, r.name)
            val localId: Long = if (existingId != null) {
                existingId
            } else {
                val roomEntity = RoomEntity(
                    id = 0,
                    name = r.name,
                    createdAt = createdAt,
                    roomType = roomType,
                    projectId = projectId
                )
                val ids = roomDao.insertAll(listOf(roomEntity))
                ids.firstOrNull()?.takeIf { it > 0 }
                    ?: roomDao.findIdByProjectAndName(projectId, r.name)
                    ?: continue
            }
            toMapRooms += r.id to localId
        }

        // GROUPS
        for (g in tree.groups) {
            val meta = g.meta.orEmpty()
            val roomUuid = meta["room_id"] as? String
            val roomLocal = roomUuid?.let { uuid.getLocalRoomId(it) } // чтение ОК вне транзакции, но быстро
            val roomName = meta["room_name"] as? String ?: ""
            val ensuredRoomId = roomLocal ?: ensurePlaceholderRoomTx(projectId, roomName)

            val entity = CircuitGroupEntity(
                groupId = 0,
                groupNumber = (meta["group_number"] as? Number)?.toInt() ?: 0,
                roomId = ensuredRoomId,
                roomName = roomName,
                groupType = (meta["group_type"] as? String) ?: "OTHER",
                nominalCurrent = (meta["nominal_current"] as? Number)?.toDouble() ?: 0.0,
                circuitBreaker = (meta["circuit_breaker"] as? Number)?.toInt() ?: 16,
                cableSection = (meta["cable_section"] as? Number)?.toDouble() ?: 2.5,
                breakerType = (meta["breaker_type"] as? String) ?: "C",
                rcdRequired = (meta["rcd_required"] as? Boolean) ?: false,
                rcdCurrent = (meta["rcd_current"] as? Number)?.toInt() ?: 30,
                createdAt = parseDate(meta["created_at_iso"] as? String),
                phase = (meta["phase"] as? String) ?: "A",
                projectId = projectId
            )
            val newLocal = groupDao.addGroup(entity)
            toMapGroups += g.id to newLocal
        }

        // DEVICES
        for (d in tree.devices) {
            val meta = d.meta.orEmpty()
            val roomUuid = meta["room_id"] as? String
            val roomLocal = roomUuid?.let { uuid.getLocalRoomId(it) }
            val ensuredRoomId = roomLocal ?: ensurePlaceholderRoomTx(projectId, meta["room_name"] as? String ?: "")

            val entity = DeviceEntity(
                deviceId = 0,
                name = d.name,
                power = (meta["power"] as? Number)?.toInt() ?: 0,
                voltage = parseVoltageFromMeta(meta),
                demandRatio = (meta["demand_ratio"] as? Number)?.toDouble() ?: 1.0,
                createdAt = parseDate(meta["created_at_iso"] as? String),
                roomId = ensuredRoomId,
                deviceType = (meta["device_type"] as? String)?.let { safeDeviceType(it) } ?: DeviceType.OTHER,
                powerFactor = (meta["power_factor"] as? Number)?.toDouble() ?: 1.0,
                hasMotor = (meta["has_motor"] as? Boolean) ?: false,
                requiresDedicatedCircuit = (meta["requires_dedicated"] as? Boolean) ?: false,
                requiresSocketConnection = (meta["requires_socket"] as? Boolean) ?: true,
                projectId = projectId
            )
            val newLocal = deviceDao.insert(entity)
            val resolvedId = if (newLocal > 0) newLocal else {
                deviceDao.findByRoomAndName(ensuredRoomId, d.name)?.deviceId ?: continue
            }
            toMapDevices += d.id to resolvedId
        }
    }

    // -------- DELTA (внутри транзакции) --------
    private suspend fun applyRoomsDeltaTx(
        projectId: String,
        delta: ProjectDeltaResponse,
        toMapRooms: MutableList<Pair<String, Long>>
    ) {
        for (r in delta.rooms.upsert) {
            val localId = uuid.getLocalRoomId(r.id)
            val createdAt = parseDate(r.meta?.get("created_at_iso") as? String)
            val roomType = (r.meta?.get("room_type") as? String)?.let { safeRoomType(it) } ?: RoomType.STANDARD

            if (localId == null) {
                val existingId = roomDao.findIdByProjectAndName(projectId, r.name)
                val finalId: Long = if (existingId != null) {
                    existingId
                } else {
                    val entity = RoomEntity(
                        id = 0,
                        name = r.name,
                        createdAt = createdAt,
                        roomType = roomType,
                        projectId = projectId
                    )
                    val ids = roomDao.insertAll(listOf(entity))
                    ids.firstOrNull()?.takeIf { it > 0 }
                        ?: roomDao.findIdByProjectAndName(projectId, r.name)
                        ?: continue
                }
                toMapRooms += r.id to finalId
            } else {
                val existing = roomDao.getRoomById(localId) ?: continue
                roomDao.updateRoom(
                    existing.copy(
                        name = r.name,
                        createdAt = createdAt,
                        roomType = roomType,
                        projectId = projectId
                    )
                )
            }
        }
        for (id in delta.rooms.delete) {
            uuid.getLocalRoomId(id)?.let { roomDao.deleteRoomById(it) }
        }
    }

    private suspend fun applyGroupsDeltaTx(
        projectId: String,
        delta: ProjectDeltaResponse,
        toMapGroups: MutableList<Pair<String, Long>>
    ) {
        for (g in delta.groups.upsert) {
            val localId = uuid.getLocalGroupId(g.id)
            val meta = g.meta.orEmpty()

            val roomUuid = meta["room_id"] as? String
            val roomLocal = roomUuid?.let { uuid.getLocalRoomId(it) }
            val roomName = meta["room_name"] as? String ?: ""

            val groupNumber = (meta["group_number"] as? Number)?.toInt() ?: 0
            val groupType = (meta["group_type"] as? String) ?: "OTHER"
            val nominalCurrent = (meta["nominal_current"] as? Number)?.toDouble() ?: 0.0
            val circuitBreaker = (meta["circuit_breaker"] as? Number)?.toInt() ?: 16
            val cableSection = (meta["cable_section"] as? Number)?.toDouble() ?: 2.5
            val breakerType = (meta["breaker_type"] as? String) ?: "C"
            val rcdRequired = (meta["rcd_required"] as? Boolean) ?: false
            val rcdCurrent = (meta["rcd_current"] as? Number)?.toInt() ?: 30
            val createdAt = parseDate(meta["created_at_iso"] as? String)
            val phase = (meta["phase"] as? String) ?: "A"

            val ensuredRoomId = roomLocal ?: ensurePlaceholderRoomTx(projectId, roomName)

            if (localId == null) {
                val entity = CircuitGroupEntity(
                    groupId = 0,
                    groupNumber = groupNumber,
                    roomId = ensuredRoomId,
                    roomName = roomName,
                    groupType = groupType,
                    nominalCurrent = nominalCurrent,
                    circuitBreaker = circuitBreaker,
                    cableSection = cableSection,
                    breakerType = breakerType,
                    rcdRequired = rcdRequired,
                    rcdCurrent = rcdCurrent,
                    createdAt = createdAt,
                    phase = phase,
                    projectId = projectId
                )
                val newLocal = groupDao.addGroup(entity)
                toMapGroups += g.id to newLocal
            } else {
                val existing = groupDao.getGroupById(localId) ?: continue
                groupDao.insertGroups(
                    listOf(
                        existing.copy(
                            groupNumber = groupNumber,
                            roomId = ensuredRoomId,
                            roomName = roomName,
                            groupType = groupType,
                            nominalCurrent = nominalCurrent,
                            circuitBreaker = circuitBreaker,
                            cableSection = cableSection,
                            breakerType = breakerType,
                            rcdRequired = rcdRequired,
                            rcdCurrent = rcdCurrent,
                            createdAt = createdAt,
                            phase = phase,
                            projectId = projectId
                        )
                    )
                )
            }
        }
        for (id in delta.groups.delete) {
            uuid.getLocalGroupId(id)?.let { groupDao.deleteGroupByGroupId(it) }
        }
    }

    private suspend fun applyDevicesDeltaTx(
        projectId: String,
        delta: ProjectDeltaResponse,
        toMapDevices: MutableList<Pair<String, Long>>
    ) {
        for (d in delta.devices.upsert) {
            val localId = uuid.getLocalDeviceId(d.id)
            val meta = d.meta.orEmpty()

            val roomUuid = meta["room_id"] as? String
            val roomLocal = roomUuid?.let { uuid.getLocalRoomId(it) }
            val ensuredRoomId = roomLocal ?: ensurePlaceholderRoomTx(projectId, meta["room_name"] as? String ?: "")

            val name = d.name
            val power = (meta["power"] as? Number)?.toInt() ?: 0
            val voltage = parseVoltageFromMeta(meta)
            val demandRatio = (meta["demand_ratio"] as? Number)?.toDouble() ?: 1.0
            val createdAt = parseDate(meta["created_at_iso"] as? String)
            val deviceType = (meta["device_type"] as? String)?.let { safeDeviceType(it) } ?: DeviceType.OTHER
            val powerFactor = (meta["power_factor"] as? Number)?.toDouble() ?: 1.0
            val hasMotor = (meta["has_motor"] as? Boolean) ?: false
            val requiresDedicated = (meta["requires_dedicated"] as? Boolean) ?: false
            val requiresSocket = (meta["requires_socket"] as? Boolean) ?: true

            if (localId == null) {
                val entity = DeviceEntity(
                    deviceId = 0,
                    name = name,
                    power = power,
                    voltage = voltage,
                    demandRatio = demandRatio,
                    createdAt = createdAt,
                    roomId = ensuredRoomId,
                    deviceType = deviceType,
                    powerFactor = powerFactor,
                    hasMotor = hasMotor,
                    requiresDedicatedCircuit = requiresDedicated,
                    requiresSocketConnection = requiresSocket,
                    projectId = projectId
                )
                val newLocal = deviceDao.insert(entity)
                val resolvedId = if (newLocal > 0) newLocal else {
                    deviceDao.findByRoomAndName(ensuredRoomId, name)?.deviceId ?: continue
                }
                toMapDevices += d.id to resolvedId
            } else {
                val existing = deviceDao.getDeviceById(localId.toInt()) ?: continue
                deviceDao.update(
                    existing.copy(
                        name = name,
                        power = power,
                        voltage = voltage,
                        demandRatio = demandRatio,
                        createdAt = createdAt,
                        roomId = ensuredRoomId,
                        deviceType = deviceType,
                        powerFactor = powerFactor,
                        hasMotor = hasMotor,
                        requiresDedicatedCircuit = requiresDedicated,
                        requiresSocketConnection = requiresSocket,
                        projectId = projectId
                    )
                )
            }
        }
        for (id in delta.devices.delete) {
            uuid.getLocalDeviceId(id)?.let { deviceDao.deleteDeviceById(it) }
        }
    }

    // -------- UUID map flush (вне транзакций) --------
    private suspend fun putUuidMappings(
        rooms: List<Pair<String, Long>>,
        groups: List<Pair<String, Long>>,
        devices: List<Pair<String, Long>>
    ) = withContext(Dispatchers.IO) {
        for ((u, l) in rooms) uuid.putRoom(u, l)
        for ((u, l) in groups) uuid.putGroup(u, l)
        for ((u, l) in devices) uuid.putDevice(u, l)
    }

    // -------- Helpers --------
    private fun iso(date: Date): String = DateTimeFormatter.ISO_INSTANT.format(date.toInstant())
    private fun isoNow(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    private fun parseDate(iso: String?): Date =
        try { Date.from(Instant.parse(iso)) } catch (_: Throwable) { Date() }

    private fun safeRoomType(v: String): RoomType =
        try { RoomType.valueOf(v) } catch (_: Throwable) { RoomType.STANDARD }

    private fun safeDeviceType(v: String): DeviceType =
        try { DeviceType.valueOf(v) } catch (_: Throwable) { DeviceType.OTHER }

    private fun parseVoltageFromMeta(meta: Map<String, Any?>): Voltage {
        val vFromValue = when (val vv = meta["voltage_value"]) {
            is Number -> vv.toInt()
            is String -> vv.toIntOrNull()
            else -> null
        }
        val vFromLegacy = when (val lv = meta["voltage"]) {
            is Number -> lv.toInt()
            is String -> lv.toIntOrNull()
            else -> null
        }
        val value = vFromValue ?: vFromLegacy ?: 230

        val typeName = (meta["voltage_type"] as? String) ?: (meta["voltage_kind"] as? String)
        val type = try {
            if (typeName != null) VoltageType.valueOf(typeName) else VoltageType.AC_1PHASE
        } catch (_: Throwable) {
            VoltageType.AC_1PHASE
        }
        return Voltage(value = value, type = type)
    }

    /** Плейсхолдер только ВНУТРИ транзакции снапшота/дельты */
    private suspend fun ensurePlaceholderRoomTx(projectId: String, roomName: String): Long {
        val name = roomName.ifBlank { "Room" }
        val byName = roomDao.findIdByProjectAndName(projectId, name)
        if (byName != null) return byName

        val entity = RoomEntity(
            id = 0,
            name = name,
            createdAt = Date(),
            roomType = RoomType.STANDARD,
            projectId = projectId
        )
        val ids = roomDao.insertAll(listOf(entity))
        return ids.firstOrNull()?.takeIf { it > 0 }
            ?: (roomDao.findIdByProjectAndName(projectId, name)
                ?: error("failed to create placeholder room"))
    }

    // ---------- PUSH (минимум: только новые) ----------
    private suspend fun buildBatchNewEntities(projectId: String): ProjectBatchRequest {
        val newRooms = mutableListOf<RoomUpsert>()
        val newGroups = mutableListOf<GroupUpsert>()
        val newDevices = mutableListOf<DeviceUpsert>()

        // Rooms — только без server UUID
        for (r in roomDao.getAllRoomsByProject(projectId)) {
            val hasUuid = uuid.getRoomUuid(r.id) != null
            if (!hasUuid) {
                newRooms += RoomUpsert(
                    id = null,
                    name = r.name,
                    meta = metaRoom(r)
                )
            }
        }

        // Groups
        for (g in groupDao.getAllGroupsByProject(projectId)) {
            val hasUuid = uuid.getGroupUuid(g.groupId) != null
            if (!hasUuid) {
                val roomUuid = uuid.getRoomUuid(g.roomId)
                newGroups += GroupUpsert(
                    id = null,
                    room_id = roomUuid,
                    name = g.roomName,
                    meta = metaGroup(g)
                )
            }
        }

        // Devices
        for (d in deviceDao.getAllDevicesByProject(projectId)) {
            val hasUuid = uuid.getDeviceUuid(d.deviceId) != null
            if (!hasUuid) {
                val roomUuid = uuid.getRoomUuid(d.roomId)
                newDevices += DeviceUpsert(
                    id = null,
                    group_id = null,
                    name = d.name,
                    meta = metaDevice(d, roomUuid)
                )
            }
        }

        val ops = if (newRooms.isEmpty() && newGroups.isEmpty() && newDevices.isEmpty()) null
        else Ops(
            rooms = OpBucket(upsert = newRooms, delete = emptyList()),
            groups = OpBucket(upsert = newGroups, delete = emptyList()),
            devices = OpBucket(upsert = newDevices, delete = emptyList())
        )

        return ProjectBatchRequest(
            baseVersion = null,
            ops = ops
        )
    }

    private fun hasOps(batch: ProjectBatchRequest): Boolean {
        val o = batch.ops ?: return false
        return (o.rooms?.upsert?.isNotEmpty() == true || o.rooms?.delete?.isNotEmpty() == true) ||
                (o.groups?.upsert?.isNotEmpty() == true || o.groups?.delete?.isNotEmpty() == true) ||
                (o.devices?.upsert?.isNotEmpty() == true || o.devices?.delete?.isNotEmpty() == true)
    }

    // ---------- meta (de)serialization ----------
    private fun metaRoom(r: RoomEntity): Map<String, Any?> = mapOf(
        "room_type" to r.roomType.name,
        "created_at_iso" to iso(r.createdAt)
    )

    private fun metaGroup(g: CircuitGroupEntity): Map<String, Any?> = mapOf(
        "room_id" to null,
        "room_name" to g.roomName,
        "group_number" to g.groupNumber,
        "group_type" to g.groupType,
        "nominal_current" to g.nominalCurrent,
        "circuit_breaker" to g.circuitBreaker,
        "cable_section" to g.cableSection,
        "breaker_type" to g.breakerType,
        "rcd_required" to g.rcdRequired,
        "rcd_current" to g.rcdCurrent,
        "created_at_iso" to iso(g.createdAt),
        "phase" to g.phase
    )

    private fun metaDevice(d: DeviceEntity, roomUuid: String?): Map<String, Any?> = mapOf(
        "room_id" to roomUuid,
        "room_name" to null,
        "power" to d.power,
        "voltage_value" to d.voltage.value,
        "voltage_type" to d.voltage.type.name,
        "demand_ratio" to d.demandRatio,
        "created_at_iso" to iso(d.createdAt),
        "device_type" to d.deviceType.name,
        "power_factor" to d.powerFactor,
        "has_motor" to d.hasMotor,
        "requires_dedicated" to d.requiresDedicatedCircuit,
        "requires_socket" to d.requiresSocketConnection
    )
}