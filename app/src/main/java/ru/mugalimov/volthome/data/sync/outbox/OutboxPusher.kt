package ru.mugalimov.volthome.data.sync.outbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.OutboxDao
import ru.mugalimov.volthome.data.local.dao.ProjectDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.dao.TombstoneDao
import ru.mugalimov.volthome.data.local.dao.UuidMapDao
import ru.mugalimov.volthome.data.local.entity.OutboxEntity
import ru.mugalimov.volthome.data.local.entity.OutboxOpType
import ru.mugalimov.volthome.data.local.entity.OutboxState
import ru.mugalimov.volthome.data.local.entity.UuidMapDevice
import ru.mugalimov.volthome.data.local.entity.UuidMapRoom
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.data.remote.dto.DeviceUpsert
import ru.mugalimov.volthome.data.remote.dto.OpBucket
import ru.mugalimov.volthome.data.remote.dto.Ops
import ru.mugalimov.volthome.data.remote.dto.ProjectBatchRequest
import ru.mugalimov.volthome.data.remote.dto.ProjectTreeDto
import ru.mugalimov.volthome.data.remote.dto.RoomUpsert
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * OutboxPusher — вытягивает записи из outbox и отправляет на сервер пачками.
 * Идемпотентность:
 *  - group_key в outbox + идемпотентное формирование batch
 *  - после успешного push тянем snapshot для выравнивания UUID-мапов
 */
@Singleton
class OutboxPusher @Inject constructor(
    private val appDb: AppDatabase,
    private val outboxDao: OutboxDao,
    private val tombstoneDao: TombstoneDao,
    private val uuidDao: UuidMapDao,
    private val projectDao: ProjectDao,
    private val roomDao: RoomDao,
    private val deviceDao: DeviceDao,
    private val projectsApi: ProjectsApi,
    @ApplicationContext private val appContext: Context,
) {

    private val pushMutex = Mutex()
    private val gson by lazy { Gson() }
    private fun jitterMs(base: Long = 100L): Long =
        base + Random.nextLong(50L, 150L)

    /** Простая проверка онлайна, чтобы не дёргать сеть оффлайн. */
    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun pushAll(): PushStats = withContext(Dispatchers.IO) {
        pushMutex.withLock {
            // 🔴 В офлайне вообще ничего не делаем: WorkManager переотложит по сетевым ограничениям воркера.
            if (!isOnline()) {
                Log.i("Outbox", "offline => skip pushAll (no DB/state changes)")
                return@withLock PushStats(total = 0, done = 0, failed = 0)
            }

            var total = 0
            var done = 0
            var failed = 0

            while (true) {
                val batch = outboxDao.pickByStates(
                    states = listOf(OutboxState.PENDING, OutboxState.FAILED_RETRYABLE),
                    limit = 200
                )
                if (batch.isEmpty()) break
                total += batch.size

                val grouped = batch.groupBy { it.project_id }
                for ((projectId, items) in grouped) {
                    try {
                        if (projectId.isNullOrBlank()) {
                            // Сейчас все операции завязаны на проект; глобальные операции обрабатывать здесь.
                            outboxDao.markState(items.map { it.id }, OutboxState.FAILED_FATAL)
                            failed += items.size
                            continue
                        }
                        val project = projectDao.getById(projectId)
                        if (project == null) {
                            // Проект локально отсутствует — помечаем фаталом
                            outboxDao.markState(items.map { it.id }, OutboxState.FAILED_FATAL)
                            failed += items.size
                            continue
                        }

                        val req = buildBatch(projectId, items)
                        if (req.ops == null) {
                            // Нечего отправлять — считаем выполненным (и точно не шлём пустой JSON)
                            outboxDao.markState(items.map { it.id }, OutboxState.DONE)
                            done += items.size
                            continue
                        }

                        try {
                            projectsApi.applyBatch(projectId, req)
                            // Успех
                            outboxDao.markState(items.map { it.id }, OutboxState.DONE)
                            refreshUuidFromSnapshot(projectId)
                            done += items.size
                        } catch (t: Throwable) {
                            Log.w("Outbox", "push failed for project=$projectId: ${t.message}", t)
                            val retryable = isRetryableError(t)
                            if (retryable) {
                                items.forEach { outboxDao.markAttempt(it.id, OutboxState.FAILED_RETRYABLE, t.message) }
                            } else {
                                outboxDao.markState(items.map { it.id }, OutboxState.FAILED_FATAL)
                            }
                            failed += items.size
                        }
                    } catch (t: Throwable) {
                        Log.w("Outbox", "group push failed: ${t.message}", t)
                        items.forEach { outboxDao.markAttempt(it.id, OutboxState.FAILED_RETRYABLE, t.message) }
                        failed += items.size
                    }

                    // лёгкий троттлинг, чтобы не долбить подряд
                    delay(jitterMs())
                }
            }
            PushStats(total = total, done = done, failed = failed)
        }
    }

    // pushProject(projectId): офлайн — выходим без изменений, как в pushAll()
    suspend fun pushProject(projectId: String): PushStats = withContext(Dispatchers.IO) {
        pushMutex.withLock {
            val items = outboxDao.pickByProject(projectId, OutboxState.PENDING, limit = 200)
                .ifEmpty { outboxDao.pickByProject(projectId, OutboxState.FAILED_RETRYABLE, limit = 200) }

            if (items.isEmpty()) return@withLock PushStats(0, 0, 0)

            if (!isOnline()) {
                Log.i("Outbox", "offline => skip pushProject (no DB/state changes) project=$projectId")
                // Раньше тут было markAttempt(... FAILED_RETRYABLE ...)
                return@withLock PushStats(total = items.size, done = 0, failed = 0)
            }

            return@withLock try {
                val req = buildBatch(projectId, items)
                if (req.ops == null) {
                    outboxDao.markState(items.map { it.id }, OutboxState.DONE)
                    PushStats(total = items.size, done = items.size, failed = 0)
                } else {
                    projectsApi.applyBatch(projectId, req)
                    outboxDao.markState(items.map { it.id }, OutboxState.DONE)
                    refreshUuidFromSnapshot(projectId)
                    PushStats(total = items.size, done = items.size, failed = 0)
                }
            } catch (t: Throwable) {
                val retryable = isRetryableError(t)
                if (retryable) {
                    items.forEach { outboxDao.markAttempt(it.id, OutboxState.FAILED_RETRYABLE, t.message) }
                } else {
                    outboxDao.markState(items.map { it.id }, OutboxState.FAILED_FATAL)
                }
                PushStats(total = items.size, done = 0, failed = items.size)
            }
        }
    }

    /** Собираем batch из группы outbox-записей одного проекта */
    private suspend fun buildBatch(projectId: String, items: List<OutboxEntity>): ProjectBatchRequest {
        val roomUps = mutableListOf<RoomUpsert>()
        val roomDel = mutableListOf<String>()

        val deviceUps = mutableListOf<DeviceUpsert>()
        val deviceDel = mutableListOf<String>()

        for (it in items) {
            when (it.op_type) {
                // ---------------- Rooms ----------------
                OutboxOpType.ROOM_CREATE -> {
                    val p = gson.fromJson(it.payload_json, RoomCreatePayload::class.java)
                    val name = p.name
                    val roomUuid = uuidDao.getRoomUuidByLocal(p.localId)
                    if (roomUuid == null) {
                        val uuid = java.util.UUID.randomUUID().toString()
                        uuidDao.putRooms(listOf(UuidMapRoom(roomUuid = uuid, localId = p.localId)))
                        roomUps += RoomUpsert(
                            id = uuid,
                            name = name,
                            meta = mapOf(
                                "room_type" to p.roomType.name,
                                "created_at_iso" to iso(Date(p.createdAt))
                            )
                        )
                    } else {
                        roomUps += RoomUpsert(
                            id = roomUuid,
                            name = name,
                            meta = mapOf(
                                "room_type" to p.roomType.name,
                                "created_at_iso" to iso(Date(p.createdAt))
                            )
                        )
                    }
                }

                OutboxOpType.ROOM_UPDATE -> {
                    val p = gson.fromJson(it.payload_json, RoomUpdatePayload::class.java)
                    val uuid = uuidDao.getRoomUuidByLocal(p.localId)
                    if (uuid != null) {
                        roomUps += RoomUpsert(
                            id = uuid,
                            name = p.name,
                            meta = mapOf("room_type" to p.roomType.name)
                        )
                    } else {
                        val newUuid = java.util.UUID.randomUUID().toString()
                        uuidDao.putRooms(listOf(UuidMapRoom(roomUuid = newUuid, localId = p.localId)))
                        roomUps += RoomUpsert(
                            id = newUuid,
                            name = p.name,
                            meta = mapOf("room_type" to p.roomType.name)
                        )
                    }
                }

                OutboxOpType.ROOM_DELETE -> {
                    val p = gson.fromJson(it.payload_json, RoomDeletePayload::class.java)
                    val uuid = p.serverUuid ?: p.localId?.let { lid -> uuidDao.getRoomUuidByLocal(lid) }
                    if (uuid != null) roomDel += uuid
                }

                // ---------------- Devices ----------------
                OutboxOpType.DEVICE_CREATE -> {
                    val p = gson.fromJson(it.payload_json, DeviceCreatePayload::class.java)
                    val hasUuid = uuidDao.getDeviceUuidByLocal(p.localId) != null
                    val roomUuid = uuidDao.getRoomUuidByLocal(p.roomLocalId)
                    if (roomUuid == null) {
                        Log.w("Outbox", "device CREATE skip: room has no uuid yet (roomLocal=${p.roomLocalId})")
                        continue
                    }
                    deviceUps += DeviceUpsert(
                        id = if (hasUuid) uuidDao.getDeviceUuidByLocal(p.localId) else null,
                        group_id = null,
                        name = p.name,
                        meta = deviceMeta(
                            roomUuid = roomUuid,
                            power = p.power,
                            voltage = p.voltage,
                            demandRatio = p.demandRatio,
                            createdAt = Date(p.createdAt),
                            deviceType = p.deviceType,
                            powerFactor = p.powerFactor,
                            hasMotor = p.hasMotor,
                            requiresDedicated = p.requiresDedicatedCircuit,
                            requiresSocket = p.requiresSocketConnection
                        )
                    )
                }

                OutboxOpType.DEVICE_UPDATE -> {
                    val p = gson.fromJson(it.payload_json, DeviceUpdatePayload::class.java)
                    val uuid = uuidDao.getDeviceUuidByLocal(p.localId)
                    val roomLocal = p.roomLocalId
                    val roomUuid = roomLocal?.let { uuidDao.getRoomUuidByLocal(it) }
                    if (uuid == null) {
                        if (roomUuid != null) {
                            deviceUps += DeviceUpsert(
                                id = null,
                                group_id = null,
                                name = p.name,
                                meta = deviceMeta(
                                    roomUuid = roomUuid,
                                    power = p.power,
                                    voltage = p.voltage,
                                    demandRatio = p.demandRatio,
                                    createdAt = Date(),
                                    deviceType = p.deviceType,
                                    powerFactor = p.powerFactor,
                                    hasMotor = p.hasMotor,
                                    requiresDedicated = p.requiresDedicatedCircuit,
                                    requiresSocket = p.requiresSocketConnection
                                )
                            )
                        } else {
                            Log.w("Outbox", "device UPDATE skip: no device uuid & no room uuid yet")
                        }
                    } else {
                        deviceUps += DeviceUpsert(
                            id = uuid,
                            group_id = null,
                            name = p.name,
                            meta = deviceMeta(
                                roomUuid = roomUuid,
                                power = p.power,
                                voltage = p.voltage,
                                demandRatio = p.demandRatio,
                                createdAt = Date(),
                                deviceType = p.deviceType,
                                powerFactor = p.powerFactor,
                                hasMotor = p.hasMotor,
                                requiresDedicated = p.requiresDedicatedCircuit,
                                requiresSocket = p.requiresSocketConnection
                            )
                        )
                    }
                }

                OutboxOpType.DEVICE_DELETE -> {
                    val p = gson.fromJson(it.payload_json, DeviceDeletePayload::class.java)
                    val uuid = p.serverUuid ?: p.localId?.let { lid -> uuidDao.getDeviceUuidByLocal(lid) }
                    if (uuid != null) deviceDel += uuid
                }

                // Projects/Groups — пока не пушим отсюда
                OutboxOpType.PROJECT_CREATE,
                OutboxOpType.PROJECT_UPDATE,
                OutboxOpType.PROJECT_DELETE,
                OutboxOpType.GROUP_CREATE,
                OutboxOpType.GROUP_UPDATE,
                OutboxOpType.GROUP_DELETE -> Unit
            }
        }

        // Формируем buckets и не шлём пустой ops
        val roomsBucket = if (roomUps.isNotEmpty() || roomDel.isNotEmpty())
            OpBucket(upsert = roomUps, delete = roomDel) else null
        val devicesBucket = if (deviceUps.isNotEmpty() || deviceDel.isNotEmpty())
            OpBucket(upsert = deviceUps, delete = deviceDel) else null

        val ops: Ops? = if (roomsBucket == null && devicesBucket == null) null
        else Ops(rooms = roomsBucket, groups = null, devices = devicesBucket)

        return ProjectBatchRequest(baseVersion = null, ops = ops)
    }

    /** После успешного пуша — тянем snapshot и обновляем uuid-маппинги */
    private suspend fun refreshUuidFromSnapshot(projectId: String) = withContext(Dispatchers.IO) {
        val tree: ProjectTreeDto = projectsApi.getProjectTree(projectId)

        // ROOMS
        val toRooms = mutableListOf<UuidMapRoom>()
        for (r in tree.rooms) {
            if (r.is_deleted) continue
            val localId = roomDao.findIdByProjectAndName(projectId, r.name) ?: continue
            if (uuidDao.getRoomUuidByLocal(localId) == null) {
                toRooms += UuidMapRoom(roomUuid = r.id, localId = localId)
            }
        }
        if (toRooms.isNotEmpty()) uuidDao.putRooms(toRooms)

        // DEVICES
        val toDevices = mutableListOf<UuidMapDevice>()
        for (d in tree.devices) {
            if (d.is_deleted) continue
            val roomUuid = (d.meta?.get("room_id") as? String) ?: continue
            val roomLocal = uuidDao.getRoomLocal(roomUuid) ?: continue
            val local = deviceDao.findByRoomAndName(roomLocal, d.name) ?: continue
            if (uuidDao.getDeviceUuidByLocal(local.deviceId) == null) {
                toDevices += UuidMapDevice(deviceUuid = d.id, localId = local.deviceId)
            }
        }
        if (toDevices.isNotEmpty()) uuidDao.putDevices(toDevices)
    }

    private fun iso(date: Date): String =
        java.time.format.DateTimeFormatter.ISO_INSTANT.format(date.toInstant())

    private fun deviceMeta(
        roomUuid: String?,
        power: Int,
        voltage: Voltage,
        demandRatio: Double,
        createdAt: Date,
        deviceType: DeviceType,
        powerFactor: Double,
        hasMotor: Boolean,
        requiresDedicated: Boolean,
        requiresSocket: Boolean
    ): Map<String, Any?> = mapOf(
        "room_id" to roomUuid,
        "room_name" to null,
        "power" to power,
        "voltage_value" to voltage.value,
        "voltage_type" to voltage.type.name,
        "demand_ratio" to demandRatio,
        "created_at_iso" to iso(createdAt),
        "device_type" to deviceType.name,
        "power_factor" to powerFactor,
        "has_motor" to hasMotor,
        "requires_dedicated" to requiresDedicated,
        "requires_socket" to requiresSocket
    )
}

data class PushStats(
    val total: Int,
    val done: Int,
    val failed: Int
)

private fun isRetryableError(t: Throwable): Boolean {
    // сетевые/SSL/таймауты — ретраим
    if (t is java.net.ConnectException ||
        t is java.net.UnknownHostException ||
        t is java.net.SocketTimeoutException ||
        t is javax.net.ssl.SSLException
    ) return true

    // HTTP — ретраим 408/409/425/429/5xx
    if (t is HttpException) {
        val code = t.code()
        if (code == 408 || code == 409 || code == 425 || code == 429) return true
        if (code in 500..599) return true
        return false // 4xx (кроме перечисленных) — фатал
    }

    // по тексту сообщения — запасной план
    val msg = t.message?.lowercase().orEmpty()
    return !(msg.contains("400") || msg.contains("401") || msg.contains("403") || msg.contains("404"))
}