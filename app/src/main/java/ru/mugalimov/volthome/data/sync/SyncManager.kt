package ru.mugalimov.volthome.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.ProjectDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.ProjectEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.local.entity.UuidMapDevice
import ru.mugalimov.volthome.data.local.entity.UuidMapGroup
import ru.mugalimov.volthome.data.local.entity.UuidMapRoom
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.data.remote.dto.ProjectDeltaResponse
import ru.mugalimov.volthome.data.remote.dto.ProjectTreeDto
import ru.mugalimov.volthome.data.sync.outbox.OutboxPusher
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

@Singleton
class SyncManager @Inject constructor(
    private val appDb: AppDatabase,
    private val projectDao: ProjectDao,
    private val roomDao: RoomDao,
    private val groupDao: GroupDao,
    private val deviceDao: DeviceDao,
    private val projectsApi: ProjectsApi,
    private val activeProjectDataStore: ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore,
    private val outboxPusher: OutboxPusher,
    @ApplicationContext private val appContext: Context, // ← для оффлайн-проверки
) {
    private val uuidDao get() = appDb.uuidMapDao()

    // per-project лок
    private val projectLocks = ConcurrentHashMap<String, Mutex>()
    private fun lockFor(projectId: String) = projectLocks.getOrPut(projectId) { Mutex() }

    // ограничиваем параллелизм синков
    private val syncSemaphore = Semaphore(1)

    private fun isDraftId(id: String): Boolean = id.startsWith("draft-")

    /** Быстрая проверка онлайна, чтобы вообще не дергать сеть оффлайн. */
    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        // Требуем и INTERNET, и VALIDATED, чтобы исключить "Captive portal / оффлайн"
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun syncActiveProject() = withContext(Dispatchers.IO) {
        val pid = activeProjectDataStore.activeProjectId.firstOrNull() ?: return@withContext
        syncProject(pid)
    }

    suspend fun syncProject(projectId: String) = withContext(Dispatchers.IO) {
        syncSemaphore.withPermit {
            lockFor(projectId).withLock {
                Log.i("Sync", "syncProject START project=$projectId thread=${Thread.currentThread().name}")
                val tStart = System.currentTimeMillis()

                try {
                    val project = projectDao.getById(projectId)
                    if (project == null) {
                        Log.w("Sync", "Project $projectId not found locally — skip")
                        return@withLock
                    }

                    if (isDraftId(projectId)) {
                        Log.i("Sync", "Project $projectId is DRAFT — skip network sync")
                        return@withLock
                    }

                    // Оффлайн — не выполняем push/pull, просто выходим (WorkManager поднимет, когда будет сеть)
                    if (!isOnline()) {
                        Log.i("Sync", "Offline detected — skip network phases for project=$projectId")
                        return@withLock
                    }

                    // 🔹 Определяем «первую загрузку» без countByProjectId:
                    //    если локальная версия == 0 или updated_at пустой — считаем, что снапшот ещё не тянули
                    val isFirstLoad = (project.version == 0) || project.updated_at.isBlank()
                    val stage = if (isFirstLoad) "snapshot" else "delta"
                    Log.i("Sync", "Sync[project=$projectId] stage=$stage")

                    // -------- PUSH (через OutboxPusher) --------
                    try {
                        val stats = outboxPusher.pushAll()
                        Log.i("Sync", "push phase done: total=${stats.total} ok=${stats.done} failed=${stats.failed}")
                    } catch (t: Throwable) {
                        Log.w("Sync", "push phase failed (will still try pull): ${t.message}", t)
                    }

                    // -------- PULL --------
                    if (isFirstLoad) {
                        // SNAPSHOT
                        val tree: ProjectTreeDto = try {
                            projectsApi.getProjectTree(projectId)
                        } catch (e: retrofit2.HttpException) {
                            if (e.code() == 400) {
                                Log.w("Sync", "snapshot -> 400 invalid_id; skip pull")
                                return@withLock
                            } else throw e
                        }

                        Log.i("Sync", "snapshot pull: rooms=${tree.rooms.size}, groups=${tree.groups.size}, devices=${tree.devices.size}")

                        val toMapRooms = mutableListOf<Pair<String, Long>>()
                        val toMapGroups = mutableListOf<Pair<String, Long>>()
                        val toMapDevices = mutableListOf<Pair<String, Long>>()

                        val t0 = System.currentTimeMillis()
                        appDb.withTransaction {
                            applySnapshotTx(
                                projectId = projectId,
                                tree = tree,
                                toMapRooms = toMapRooms,
                                toMapGroups = toMapGroups,
                                toMapDevices = toMapDevices
                            )
                            // фиксируем «истину сервера»
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
                        Log.i("SyncTx", "snapshot tx ms=${System.currentTimeMillis() - t0}")

                        putUuidMappings(toMapRooms, toMapGroups, toMapDevices)
                    } else {
                        // DELTA
                        val since = project.updated_at.ifBlank { "1970-01-01T00:00:00Z" }
                        val delta: ProjectDeltaResponse = try {
                            projectsApi.getDelta(projectId, since)
                        } catch (e: retrofit2.HttpException) {
                            if (e.code() == 400) {
                                Log.w("Sync", "delta(since=$since) -> 400 invalid_id; skip pull")
                                return@withLock
                            } else throw e
                        }

                        Log.i(
                            "Sync",
                            "delta pull: rooms.upsert=${delta.rooms.upsert.size}, rooms.delete=${delta.rooms.delete.size}, " +
                                    "groups.upsert=${delta.groups.upsert.size}, groups.delete=${delta.groups.delete.size}, " +
                                    "devices.upsert=${delta.devices.upsert.size}, devices.delete=${delta.devices.delete.size}"
                        )

                        val toMapRooms = mutableListOf<Pair<String, Long>>()
                        val toMapGroups = mutableListOf<Pair<String, Long>>()
                        val toMapDevices = mutableListOf<Pair<String, Long>>()

                        appDb.withTransaction {
                            applyRoomsDeltaTx(projectId, delta, toMapRooms)
                            applyGroupsDeltaTx(projectId, delta, toMapGroups)
                            applyDevicesDeltaTx(projectId, delta, toMapDevices)

                            // обновим только updated_at (или версию, если она приходит где-то в другом месте)
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

                        putUuidMappings(toMapRooms, toMapGroups, toMapDevices)
                    }
                } finally {
                    val ms = System.currentTimeMillis() - tStart
                    Log.i("Sync", "syncProject END project=$projectId in ${ms}ms")
                }
            }
        }
    }

    // ----------------------- SNAPSHOT -----------------------
    private suspend fun applySnapshotTx(
        projectId: String,
        tree: ProjectTreeDto,
        toMapRooms: MutableList<Pair<String, Long>>,
        toMapGroups: MutableList<Pair<String, Long>>,
        toMapDevices: MutableList<Pair<String, Long>>
    ) {
        val roomUuidToLocal = mutableMapOf<String, Long>()

        // ROOMS
        for (r in tree.rooms) {
            if (r.is_deleted) {
                uuidDao.getRoomLocal(r.id)?.let { roomDao.deleteRoomById(it) }
                continue
            }

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
            roomUuidToLocal[r.id] = localId
        }

        val roomNameToLocalId: Map<String, Long> = tree.rooms.associateNotNull { rd ->
            val local = roomDao.findIdByProjectAndName(projectId, rd.name)
            if (local != null && !rd.is_deleted) rd.name to local else null
        }

        // GROUPS
        for (g in tree.groups) {
            if (g.is_deleted) {
                uuidDao.getGroupLocal(g.id)?.let { groupDao.deleteGroupByGroupId(it) }
                continue
            }

            val meta = g.meta.orEmpty()
            val roomUuid = meta["room_id"] as? String
            val roomLocal = roomUuid?.let { roomUuidToLocal[it] }
            val roomName = (meta["room_name"] as? String)?.trim().orEmpty()

            val ensuredRoomId = when {
                roomLocal != null -> roomLocal
                roomName.isNotEmpty() -> ensureRoomByNameTx(projectId, roomName)
                else -> ensurePlaceholderRoomTx(projectId, "Room")
            }

            val entity = CircuitGroupEntity(
                groupId = 0,
                groupNumber = (meta["group_number"] as? Number)?.toInt() ?: 0,
                roomId = ensuredRoomId,
                roomName = if (roomName.isNotEmpty()) roomName else "Room",
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
            if (d.is_deleted) {
                uuidDao.getDeviceLocal(d.id)?.let { deviceDao.deleteDeviceById(it) }
                continue
            }

            val meta = d.meta.orEmpty()
            val roomUuid = meta["room_id"] as? String
            val roomName = (meta["room_name"] as? String)?.trim().orEmpty()

            val roomLocalFromUuid = roomUuid?.let { roomUuidToLocal[it] }
            val roomLocalFromName = if (roomLocalFromUuid == null && roomName.isNotEmpty()) {
                roomNameToLocalId[roomName] ?: roomDao.findIdByProjectAndName(projectId, roomName)
            } else null

            val ensuredRoomId = when {
                roomLocalFromUuid != null -> roomLocalFromUuid
                roomLocalFromName != null -> roomLocalFromName
                roomName.isNotEmpty() -> ensureRoomByNameTx(projectId, roomName)
                else -> ensurePlaceholderRoomTx(projectId, "Room")
            }

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
            val resolvedId = if (newLocal > 0) newLocal
            else deviceDao.findByRoomAndName(ensuredRoomId, d.name)?.deviceId ?: continue

            toMapDevices += d.id to resolvedId
        }
    }

    // ----------------------- DELТА -----------------------
    private suspend fun applyRoomsDeltaTx(
        projectId: String,
        delta: ProjectDeltaResponse,
        toMapRooms: MutableList<Pair<String, Long>>
    ) {
        for (r in delta.rooms.upsert) {
            if (r.is_deleted) {
                uuidDao.getRoomLocal(r.id)?.let { roomDao.deleteRoomById(it) }
                continue
            }
            val localId = uuidDao.getRoomLocal(r.id)
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
            uuidDao.getRoomLocal(id)?.let { roomDao.deleteRoomById(it) }
        }
    }

    private suspend fun applyGroupsDeltaTx(
        projectId: String,
        delta: ProjectDeltaResponse,
        toMapGroups: MutableList<Pair<String, Long>>
    ) {
        for (g in delta.groups.upsert) {
            if (g.is_deleted) {
                uuidDao.getGroupLocal(g.id)?.let { groupDao.deleteGroupByGroupId(it) }
                continue
            }
            val localId = uuidDao.getGroupLocal(g.id)
            val meta = g.meta.orEmpty()

            val roomUuid = meta["room_id"] as? String
            val roomLocal = roomUuid?.let { uuidDao.getRoomLocal(it) }
            val roomName = (meta["room_name"] as? String)?.trim().orEmpty()

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

            val ensuredRoomId: Long = when {
                roomLocal != null -> roomLocal
                roomName.isNotEmpty() -> {
                    val byName = roomDao.findIdByProjectAndName(projectId, roomName)
                    if (byName == null) {
                        Log.w("Sync", "groups.upsert skip: room not found for name='$roomName', project=$projectId")
                        continue
                    }
                    byName
                }
                else -> {
                    Log.w("Sync", "groups.upsert skip: no room_id and no room_name for group_number=$groupNumber")
                    continue
                }
            }

            if (localId == null) {
                val entity = CircuitGroupEntity(
                    groupId = 0,
                    groupNumber = groupNumber,
                    roomId = ensuredRoomId,
                    roomName = if (roomName.isNotEmpty()) roomName else "Room",
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
                            roomName = if (roomName.isNotEmpty()) roomName else "Room",
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
            uuidDao.getGroupLocal(id)?.let { groupDao.deleteGroupByGroupId(it) }
        }
    }

    private suspend fun applyDevicesDeltaTx(
        projectId: String,
        delta: ProjectDeltaResponse,
        toMapDevices: MutableList<Pair<String, Long>>
    ) {
        for (d in delta.devices.upsert) {
            if (d.is_deleted) {
                uuidDao.getDeviceLocal(d.id)?.let { deviceDao.deleteDeviceById(it) }
                continue
            }

            val localId = uuidDao.getDeviceLocal(d.id)
            val meta = d.meta.orEmpty()

            val roomUuid: String? = meta["room_id"] as? String
            val roomName: String = (meta["room_name"] as? String)?.trim().orEmpty()

            var ensuredRoomId: Long? = roomUuid?.let { uuidDao.getRoomLocal(it) }
            if (ensuredRoomId == null && roomName.isNotEmpty()) {
                ensuredRoomId = roomDao.findIdByProjectAndName(projectId, roomName)
            }
            if (ensuredRoomId == null) {
                Log.w(
                    "Sync",
                    "devices.upsert skip: cannot resolve room for device='${d.name}' " +
                            "roomUuid=$roomUuid roomName='$roomName' project=$projectId"
                )
                continue
            }

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
                val existingByName = deviceDao.findByRoomAndName(ensuredRoomId!!, name)
                if (existingByName != null) {
                    deviceDao.update(
                        existingByName.copy(
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
                    toMapDevices += d.id to existingByName.deviceId
                } else {
                    val entity = DeviceEntity(
                        deviceId = 0,
                        name = name,
                        power = power,
                        voltage = voltage,
                        demandRatio = demandRatio,
                        createdAt = createdAt,
                        roomId = ensuredRoomId!!,
                        deviceType = deviceType,
                        powerFactor = powerFactor,
                        hasMotor = hasMotor,
                        requiresDedicatedCircuit = requiresDedicated,
                        requiresSocketConnection = requiresSocket,
                        projectId = projectId
                    )
                    val newLocal = deviceDao.insert(entity)
                    val resolvedId = if (newLocal > 0) newLocal
                    else deviceDao.findByRoomAndName(ensuredRoomId!!, name)?.deviceId ?: continue

                    toMapDevices += d.id to resolvedId
                }
            } else {
                val existing = deviceDao.getDeviceById(localId.toInt()) ?: continue
                deviceDao.update(
                    existing.copy(
                        name = name,
                        power = power,
                        voltage = voltage,
                        demandRatio = demandRatio,
                        createdAt = createdAt,
                        roomId = ensuredRoomId!!,
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
            uuidDao.getDeviceLocal(id)?.let { deviceDao.deleteDeviceById(it) }
        }
    }

    // ----------------- UUID map flush (вне транзакций) -----------------
    private suspend fun putUuidMappings(
        rooms: List<Pair<String, Long>>,
        groups: List<Pair<String, Long>>,
        devices: List<Pair<String, Long>>
    ) = withContext(Dispatchers.IO) {
        if (rooms.isNotEmpty())
            uuidDao.putRooms(rooms.map { (u, l) -> UuidMapRoom(roomUuid = u, localId = l) })
        if (groups.isNotEmpty())
            uuidDao.putGroups(groups.map { (u, l) -> UuidMapGroup(groupUuid = u, localId = l) })
        if (devices.isNotEmpty())
            uuidDao.putDevices(devices.map { (u, l) -> UuidMapDevice(deviceUuid = u, localId = l) })
    }

    // ------------------------- Helpers -------------------------
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
            if (typeName != null) VoltageType.valueOf(typeName)
            else VoltageType.AC_1PHASE
        } catch (_: Throwable) {
            VoltageType.AC_1PHASE
        }
        return Voltage(value = value, type = type)
    }

    /** Создать/вернуть комнату по имени (внутри транзакции снапшота/дельты) */
    private suspend fun ensureRoomByNameTx(projectId: String, roomName: String): Long {
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
                ?: error("failed to create room by name"))
    }

    /** Плейсхолдер-комната (внутри транзакции) */
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
}

/** local утилита, чтобы не тянуть kotlin-stdlib extensions */
private inline fun <T, K, V> Iterable<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> {
    val map = LinkedHashMap<K, V>()
    for (e in this) {
        val p = transform(e)
        if (p != null) map[p.first] = p.second
    }
    return map
}