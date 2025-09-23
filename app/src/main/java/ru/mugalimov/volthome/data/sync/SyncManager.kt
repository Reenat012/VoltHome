package ru.mugalimov.volthome.data.sync

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
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
import ru.mugalimov.volthome.data.remote.dto.DeviceUpsert
import ru.mugalimov.volthome.data.remote.dto.GroupUpsert
import ru.mugalimov.volthome.data.remote.dto.OpBucket
import ru.mugalimov.volthome.data.remote.dto.Ops
import ru.mugalimov.volthome.data.remote.dto.ProjectBatchRequest
import ru.mugalimov.volthome.data.remote.dto.ProjectDeltaResponse
import ru.mugalimov.volthome.data.remote.dto.RoomUpsert
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

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

    /** Синк текущего активного проекта из DataStore */
    suspend fun syncActiveProject() = withContext(Dispatchers.IO) {
        val projectId = activeProjectDataStore.activeProjectId.firstOrNull() ?: return@withContext
        syncByProjectId(projectId)
    }

    /** Синк конкретного проекта по его UUID (не меняет ActiveProjectDataStore) */
    suspend fun syncProject(projectId: String) = withContext(Dispatchers.IO) {
        syncByProjectId(projectId)
    }

    // ---- Общая реализация синка по id ----
    private suspend fun syncByProjectId(projectId: String) {
        val project = projectDao.getById(projectId) ?: return

        // PULL
        val since = project.updated_at.ifBlank { "1970-01-01T00:00:00Z" }
        val delta: ProjectDeltaResponse = projectsApi.getDelta(projectId, since)

        appDb.withTransaction {
            applyRoomsDelta(projectId, delta)
            applyGroupsDelta(projectId, delta)
            applyDevicesDelta(projectId, delta)

            // продвигаем updated_at — чтобы next since не был пустым
            projectDao.upsert(
                ProjectEntity(
                    id = project.id,
                    name = project.name,
                    note = project.note,
                    version = project.version,
                    updated_at = isoNow(),
                    is_deleted = project.is_deleted
                )
            )
        }

        // PUSH: только новые сущности без серверных UUID
        val batch = buildBatchNewEntities(projectId)
        if (batch.ops != null && hasOps(batch)) {
            projectsApi.applyBatch(projectId, batch)
        }
    }

    // ---------- PULL: Rooms ----------
    private suspend fun applyRoomsDelta(projectId: String, delta: ProjectDeltaResponse) {
        for (r in delta.rooms.upsert) {
            val localId = uuid.getLocalRoomId(r.id)
            val createdAt = parseDate(r.meta?.get("created_at_iso") as? String)
            val roomType = (r.meta?.get("room_type") as? String)?.let { safeRoomType(it) } ?: RoomType.STANDARD

            if (localId == null) {
                val entity = RoomEntity(
                    id = 0,
                    name = r.name,
                    createdAt = createdAt,
                    roomType = roomType,
                    projectId = projectId
                )
                val ids = roomDao.insertAll(listOf(entity))
                val newLocal = ids.firstOrNull()?.takeIf { it > 0 } ?: run {
                    val existing = roomDao.getAllRoomsByProject(projectId).firstOrNull { it.name == r.name }
                    existing?.id ?: continue
                }
                uuid.putRoom(r.id, newLocal)
            } else {
                val existing = roomDao.getRoomById(localId) ?: continue
                val updated = existing.copy(
                    name = r.name,
                    createdAt = createdAt,
                    roomType = roomType,
                    projectId = projectId
                )
                roomDao.updateRoom(updated)
            }
        }
        for (id in delta.rooms.delete) {
            uuid.getLocalRoomId(id)?.let { roomDao.deleteRoomById(it) }
        }
    }

    // ---------- PULL: Groups ----------
    private suspend fun applyGroupsDelta(projectId: String, delta: ProjectDeltaResponse) {
        for (g in delta.groups.upsert) {
            val localId = uuid.getLocalGroupId(g.id)
            val meta = g.meta ?: emptyMap<String, Any?>()

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

            val ensuredRoomId = roomLocal ?: ensurePlaceholderRoom(projectId, roomName)

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
                uuid.putGroup(g.id, newLocal)
            } else {
                val existing = groupDao.getGroupById(localId) ?: continue
                val updated = existing.copy(
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
                groupDao.insertGroups(listOf(updated))
            }
        }
        for (id in delta.groups.delete) {
            uuid.getLocalGroupId(id)?.let { groupDao.deleteGroupByGroupId(it) }
        }
    }

    // ---------- PULL: Devices ----------
    private suspend fun applyDevicesDelta(projectId: String, delta: ProjectDeltaResponse) {
        for (d in delta.devices.upsert) {
            val localId = uuid.getLocalDeviceId(d.id)
            val meta = d.meta ?: emptyMap<String, Any?>()

            val roomUuid = meta["room_id"] as? String
            val roomLocal = roomUuid?.let { uuid.getLocalRoomId(it) }
            val ensuredRoomId = roomLocal ?: ensurePlaceholderRoom(projectId, meta["room_name"] as? String ?: "")

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
                uuid.putDevice(d.id, resolvedId)
            } else {
                val existing = deviceDao.getDeviceById(localId.toInt()) ?: continue
                val updated = existing.copy(
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
                deviceDao.update(updated)
            }
        }
        for (id in delta.devices.delete) {
            uuid.getLocalDeviceId(id)?.let { deviceDao.deleteDeviceById(it) }
        }
    }

    // ---------- PUSH (минимум: только новые) ----------
    private suspend fun buildBatchNewEntities(projectId: String): ProjectBatchRequest {
        val newRooms = mutableListOf<RoomUpsert>()
        val newGroups = mutableListOf<GroupUpsert>()
        val newDevices = mutableListOf<DeviceUpsert>()

        // Rooms
        val rooms = roomDao.getAllRoomsByProject(projectId)
        for (r in rooms) {
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
        val groups = groupDao.getAllGroupsByProject(projectId)
        for (g in groups) {
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
        val devices = deviceDao.getAllDevicesByProject(projectId)
        for (d in devices) {
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

    // ---------- util: ensure placeholder room ----------
    private suspend fun ensurePlaceholderRoom(projectId: String, roomName: String): Long {
        val existing = roomDao.getAllRoomsByProject(projectId).firstOrNull { it.name == roomName }
        if (existing != null) return existing.id

        val entity = RoomEntity(
            id = 0,
            name = if (roomName.isNotBlank()) roomName else "Room",
            createdAt = Date(),
            roomType = RoomType.STANDARD,
            projectId = projectId
        )
        val ids = roomDao.insertAll(listOf(entity))
        return ids.firstOrNull()?.takeIf { it > 0 } ?: run {
            roomDao.getAllRoomsByProject(projectId).first { it.name == entity.name }.id
        }
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

    // ---------- helpers ----------
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
}