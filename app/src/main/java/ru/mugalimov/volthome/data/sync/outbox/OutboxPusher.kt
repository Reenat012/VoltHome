package ru.mugalimov.volthome.data.sync.outbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.room.withTransaction
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
import ru.mugalimov.volthome.data.remote.api.CreateProjectRequest
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.data.remote.api.ProjectsApi.UpdateProjectRequest
import ru.mugalimov.volthome.data.remote.dto.DeviceUpsert
import ru.mugalimov.volthome.data.remote.dto.OpBucket
import ru.mugalimov.volthome.data.remote.dto.Ops
import ru.mugalimov.volthome.data.remote.dto.ProjectBatchRequest
import ru.mugalimov.volthome.data.remote.dto.ProjectTreeDto
import ru.mugalimov.volthome.data.remote.dto.RoomUpsert
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.util.TimeUtils
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * OutboxPusher — вытягивает записи из outbox и отправляет на сервер пачками.
 * Особенности:
 *  • Оффлайн guard: ничего не делаем без валидного онлайна — WorkManager поднимет заново.
 *  • Публикация драфта: если группа outbox относится к draft-*, сначала выполняем PROJECT_CREATE
 *    (с учётом последнего PROJECT_UPDATE для имени/заметки), затем ребиндим локальные сущности и
 *    только после этого пушим оставшийся батч на remoteId.
 *  • Удаление проекта: PROJECT_DELETE обрабатывается как для draft-* (локально), так и для серверного UUID (через DELETE API).
 *  • Идемпотентность: group_key + аккуратные DONE/FAILED_RETRYABLE/FAILED_FATAL.
 *  • Вариант B: batch отправляется в два прохода — сначала rooms, затем devices.
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
    private companion object {
        const val ERR_PRO_REQUIRED_PROJECTS_LIMIT = "pro_required_projects_limit"
    }
    private fun jitterMs(base: Long = 100L): Long = base + Random.nextLong(50L, 150L)
    private data class ErrorBodyDto(
        val error: String? = null
    )

    private fun extract402ErrorCode(t: Throwable): String? {
        val e = t as? HttpException ?: return null
        if (e.code() != 402) return null

        return try {
            val raw = e.response()?.errorBody()?.string().orEmpty()
            if (raw.isBlank()) return null
            gson.fromJson(raw, ErrorBodyDto::class.java)?.error
        } catch (_: Throwable) {
            null
        }
    }

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
            if (!isOnline()) {
                Log.i("Outbox", "offline => skip pushAll (no DB/state changes)")
                return@withLock PushStats(total = 0, done = 0, failed = 0)
            }

            var total = 0
            var done = 0
            var failed = 0

            var madeProgress: Boolean
            do {
                madeProgress = false

                val batch = outboxDao.pickByStates(
                    states = listOf(OutboxState.PENDING, OutboxState.FAILED_RETRYABLE),
                    limit = 200
                )
                if (batch.isEmpty()) break

                val grouped = batch.groupBy { it.project_id }
                for ((rawProjectId, items) in grouped) {
                    try {
                        val result = pushOneProjectGroup(rawProjectId, items)
                        total += items.size
                        done  += result.done
                        failed += result.failed
                        if (result.done > 0 || result.failed > 0) madeProgress = true
                    } catch (t: Throwable) {
                        Log.w("Outbox", "group push failed: ${t.message}", t)
                        items.forEach { outboxDao.markAttempt(it.id, OutboxState.FAILED_RETRYABLE, t.message) }
                        total += items.size
                        failed += items.size
                        madeProgress = true
                    }
                    delay(jitterMs())
                }
            } while (madeProgress)
            PushStats(total = total, done = done, failed = failed)
        }
    }

    suspend fun pushProject(projectId: String): PushStats = withContext(Dispatchers.IO) {
        pushMutex.withLock {
            val items = outboxDao.pickByProject(projectId, OutboxState.PENDING, limit = 200)
                .ifEmpty { outboxDao.pickByProject(projectId, OutboxState.FAILED_RETRYABLE, limit = 200) }

            if (items.isEmpty()) return@withLock PushStats(0, 0, 0)
            if (!isOnline()) {
                Log.i("Outbox", "offline => skip pushProject (no DB/state changes) project=$projectId")
                return@withLock PushStats(total = items.size, done = 0, failed = 0)
            }

            // 🔒 NEW: не пушим в проект, помеченный на удаление
            val p = projectDao.getById(projectId)
            if (p == null) {
                outboxDao.markState(items.map { it.id }, OutboxState.FAILED_FATAL)
                return@withLock PushStats(total = items.size, done = 0, failed = items.size)
            }
            if (p.is_deleted) {
                val deleteOnly = items.filter { it.op_type == OutboxOpType.PROJECT_DELETE }
                val others     = items.filter { it.op_type != OutboxOpType.PROJECT_DELETE }

                if (deleteOnly.isEmpty()) {
                    Log.w("Outbox", "skip push (deleted project, no PROJECT_DELETE): id=$projectId")
                    outboxDao.markState(others.map { it.id }, OutboxState.FAILED_FATAL)
                    return@withLock PushStats(total = items.size, done = 0, failed = others.size)
                }

                // Пушим только удаление; остальное — фатально
                outboxDao.markState(others.map { it.id }, OutboxState.FAILED_FATAL)
                return@withLock try {
                    pushOneProjectGroup(projectId, deleteOnly)
                } catch (t: Throwable) {
                    val retryable = isRetryableError(t)
                    if (retryable) {
                        deleteOnly.forEach { outboxDao.markAttempt(it.id, OutboxState.FAILED_RETRYABLE, t.message) }
                    } else {
                        outboxDao.markState(deleteOnly.map { it.id }, OutboxState.FAILED_FATAL)
                    }
                    PushStats(total = deleteOnly.size, done = 0, failed = deleteOnly.size)
                }
            }

            return@withLock try {
                pushOneProjectGroup(projectId, items)
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

    /** Обработка одной проектной группы outbox-записей */
    private suspend fun pushOneProjectGroup(rawProjectId: String?, items: List<OutboxEntity>): PushStats {
        if (rawProjectId.isNullOrBlank()) {
            outboxDao.markState(items.map { it.id }, OutboxState.FAILED_FATAL)
            return PushStats(total = items.size, done = 0, failed = items.size)
        }

        val project = projectDao.getById(rawProjectId)
        if (project == null) {
            outboxDao.markState(items.map { it.id }, OutboxState.FAILED_FATAL)
            return PushStats(total = items.size, done = 0, failed = items.size)
        }

        if (project.is_deleted) {
            val deleteOnly = items.filter { it.op_type == OutboxOpType.PROJECT_DELETE }
            val others     = items.filter { it.op_type != OutboxOpType.PROJECT_DELETE }

            if (deleteOnly.isEmpty()) {
                Log.w("Outbox", "skip push: project is deleted (id=${project.id})")
                outboxDao.markState(others.map { it.id }, OutboxState.FAILED_FATAL)
                return PushStats(total = items.size, done = 0, failed = others.size)
            }

            // Пропускаем дальше только ветку удаления проекта
            if (others.isNotEmpty()) outboxDao.markState(others.map { it.id }, OutboxState.FAILED_FATAL)
            // и продолжаем обычный сценарий — в нём PROJECT_DELETE имеет приоритет, удаление отработает.
        }

        var effectiveProjectId = rawProjectId
        var remainingItems = items

        // -------- Публикация драфта, если требуется (с учётом последнего PROJECT_UPDATE) --------
        if (rawProjectId.startsWith("draft-")) {
            val createItem = items.firstOrNull { it.op_type == OutboxOpType.PROJECT_CREATE }
            if (createItem == null) {
                Log.d("Outbox", "draft group without PROJECT_CREATE → skip (no-op): project=$rawProjectId")
                return PushStats(total = items.size, done = 0, failed = 0)
            }

            try {
                val createPayload = gson.fromJson(createItem.payload_json, ProjectCreatePayload::class.java)
                // если был PROJECT_UPDATE до публикации — берём самое позднее имя/заметку
                val latestUpdate = items.lastOrNull { it.op_type == OutboxOpType.PROJECT_UPDATE }
                val upd = latestUpdate?.let { gson.fromJson(it.payload_json, ProjectUpdatePayload::class.java) }

                val createName = upd?.name ?: createPayload.name
                val createNote = upd?.note ?: createPayload.note

                Log.i("Outbox", "publishing draft project: localId=$rawProjectId, name='$createName'")

                val created = projectsApi.createProject(CreateProjectRequest(name = createName, note = createNote))
                val remoteId = created.id
                Log.i("Outbox", "draft published ok: remoteId=$remoteId (v${created.version})")

                val groupDaoOrNull = runCatching { appDb.groupDao() }.getOrNull()
                val stateDao = appDb.projectLocalStateDao()

                appDb.withTransaction {
                    projectDao.upsert(
                        ru.mugalimov.volthome.data.local.entity.ProjectEntity(
                            id = created.id,
                            name = created.name,
                            note = created.note,
                            version = created.version,
                            updated_at = created.updated_at,
                            is_deleted = created.is_deleted
                        )
                    )
                    val roomsRebound = roomDao.rebindProjectRooms(rawProjectId, remoteId)
                    val devicesRebound = deviceDao.rebindProjectDevices(rawProjectId, remoteId)
                    val groupsRebound = if (groupDaoOrNull != null) {
                        runCatching { groupDaoOrNull.rebindProjectGroups(rawProjectId, remoteId) }.getOrElse { 0 }
                    } else 0
                    Log.i("Outbox", "rebind done: rooms=$roomsRebound, devices=$devicesRebound, groups=$groupsRebound")

                    stateDao.delete(rawProjectId)
                    stateDao.upsert(
                        ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity(
                            project_id = created.id,
                            remote_version = created.version,
                            last_sync_at = TimeUtils.formatIso(TimeUtils.now()),
                            has_local_changes = true
                        )
                    )

                    projectDao.deleteById(rawProjectId)
                }

                outboxDao.markState(listOf(createItem.id), OutboxState.DONE)
                // Если использовали PROJECT_UPDATE при публикации — он уже учтён, пометим DONE
                val toSkipIds = mutableSetOf(createItem.id)
                if (latestUpdate != null) {
                    outboxDao.markState(listOf(latestUpdate.id), OutboxState.DONE)
                    toSkipIds.add(latestUpdate.id)
                }

                effectiveProjectId = remoteId
                remainingItems = items.filter { it.id !in toSkipIds }
            } catch (t: Throwable) {
                Log.w("Outbox", "draft publish failed: ${t.message}", t)

                // ✅ Шаг 1.3: 402 + {error:"pro_required_projects_limit"} => FAILED_FATAL + last_error=код
                val code = extract402ErrorCode(t)
                if (code == ERR_PRO_REQUIRED_PROJECTS_LIMIT) {
                    outboxDao.markAttempt(createItem.id, OutboxState.FAILED_FATAL, code)
                    return PushStats(total = remainingItems.size + 1, done = 0, failed = 1)
                }

                val retryable = isRetryableError(t)
                if (retryable) {
                    outboxDao.markAttempt(createItem.id, OutboxState.FAILED_RETRYABLE, t.message)
                } else {
                    outboxDao.markAttempt(createItem.id, OutboxState.FAILED_FATAL, t.message)
                }
                return PushStats(total = remainingItems.size + 1, done = 0, failed = if (retryable) 0 else 1)
            }
        }

        // -------- Удаление проекта (PROJECT_DELETE) имеет приоритет --------
        val deleteItem = remainingItems.firstOrNull { it.op_type == OutboxOpType.PROJECT_DELETE }
        if (deleteItem != null) {
            val idForDelete = effectiveProjectId // после публикации драфта уже серверный ID

            try {
                if (idForDelete.startsWith("draft-")) {
                    Log.i("Outbox", "delete local draft project=$idForDelete")
                    projectDao.softDelete(
                        id = idForDelete,
                        updatedAt = TimeUtils.formatIso(TimeUtils.now()),
                        version = project.version
                    )
                } else {
                    Log.i("Outbox", "delete remote project=$idForDelete via API")
                    projectsApi.deleteProject(idForDelete)
                    projectDao.softDelete(
                        id = idForDelete,
                        updatedAt = TimeUtils.formatIso(TimeUtils.now()),
                        version = project.version
                    )
                }

                outboxDao.markState(listOf(deleteItem.id), OutboxState.DONE)
                val rest = remainingItems.filter { it.id != deleteItem.id }
                if (rest.isNotEmpty()) outboxDao.markState(rest.map { it.id }, OutboxState.DONE)

                return PushStats(total = items.size, done = items.size, failed = 0)
            } catch (t: Throwable) {
                Log.w("Outbox", "project delete failed: ${t.message}", t)
                val retryable = isRetryableError(t)
                if (retryable) {
                    outboxDao.markAttempt(deleteItem.id, OutboxState.FAILED_RETRYABLE, t.message)
                } else {
                    outboxDao.markState(listOf(deleteItem.id), OutboxState.FAILED_FATAL)
                }
                return PushStats(total = items.size, done = 0, failed = 1)
            }
        }

        // -------- Применяем все PROJECT_UPDATE для серверного проекта (до batch) --------
        val updates = remainingItems.filter { it.op_type == OutboxOpType.PROJECT_UPDATE }
        if (updates.isNotEmpty()) {
            try {
                // last-write-wins: берём последний апдейт
                val last = updates.last()
                val up = gson.fromJson(last.payload_json, ProjectUpdatePayload::class.java)

                val updated = updateProjectMetaWithFallback(
                    projectId = effectiveProjectId,
                    name = up.name,
                    note = up.note
                )

                // синхронизируем локально
                projectDao.upsert(
                    ru.mugalimov.volthome.data.local.entity.ProjectEntity(
                        id = updated.id,
                        name = updated.name,
                        note = updated.note,
                        version = updated.version,
                        updated_at = updated.updated_at,
                        is_deleted = updated.is_deleted
                    )
                )

                // все PROJECT_UPDATE считаем применёнными
                outboxDao.markState(updates.map { it.id }, OutboxState.DONE)
                remainingItems = remainingItems.filter { it.op_type != OutboxOpType.PROJECT_UPDATE }
            } catch (t: Throwable) {
                Log.w("Outbox", "project update failed: ${t.message}", t)
                val retryable = isRetryableError(t)
                if (retryable) {
                    updates.forEach { outboxDao.markAttempt(it.id, OutboxState.FAILED_RETRYABLE, t.message) }
                } else {
                    outboxDao.markState(updates.map { it.id }, OutboxState.FAILED_FATAL)
                }
                // продолжим к batch с остальными типами операций
            }
        }

        // -------- Оставшиеся операции → двухпроходный batch --------
        if (remainingItems.isEmpty()) {
            return PushStats(total = 0, done = 0, failed = 0)
        }

        return try {
            // 1) сначала комнаты
            val roomsReq = buildBatchSelective(effectiveProjectId, remainingItems, includeRooms = true, includeDevices = false)
            if (roomsReq.ops?.rooms != null) {
                projectsApi.applyBatch(effectiveProjectId, roomsReq)
                refreshUuidFromSnapshot(effectiveProjectId)
            }

            // 2) затем устройства
            val devicesReq = buildBatchSelective(effectiveProjectId, remainingItems, includeRooms = false, includeDevices = true)

            // 🔒 Клиентская валидация: все devices.upsert должны иметь непустой id
            devicesReq.ops?.devices?.upsert?.forEachIndexed { i, d ->
                if (d.id.isBlank()) {
                    Log.e("Outbox", "Validation failed: devices.upsert[$i].id is blank — batch not sent")
                    throw IllegalStateException("DeviceUpsert.id is required")
                }
            }

            if (devicesReq.ops?.devices != null) {
                projectsApi.applyBatch(effectiveProjectId, devicesReq)
                refreshUuidFromSnapshot(effectiveProjectId)
            }

            // помечаем весь набор как DONE (rooms/devices/прочие в remainingItems)
            outboxDao.markState(remainingItems.map { it.id }, OutboxState.DONE)
            PushStats(total = remainingItems.size, done = remainingItems.size, failed = 0)
        } catch (t: Throwable) {
            Log.w("Outbox", "push failed for project=$effectiveProjectId: ${t.message}", t)
            val retryable = isRetryableError(t)
            if (retryable) {
                remainingItems.forEach { outboxDao.markAttempt(it.id, OutboxState.FAILED_RETRYABLE, t.message) }
            } else {
                outboxDao.markState(remainingItems.map { it.id }, OutboxState.FAILED_FATAL)
            }
            PushStats(total = remainingItems.size, done = 0, failed = remainingItems.size)
        }
    }

    /**
     * Три последовательные попытки обновить метаданные проекта:
     *  1) PATCH /v1/projects/{id}/meta
     *  2) PUT   /v1/projects/{id}/meta
     *  3) PUT   /v1/projects/{id}
     */
    private suspend fun updateProjectMetaWithFallback(
        projectId: String,
        name: String?,
        note: String?
    ) = run {
        val body = UpdateProjectRequest(name = name, note = note)

        // 1) PATCH /meta
        try {
            return@run projectsApi.updateProjectMetaPatch(projectId, body)
        } catch (e: HttpException) {
            if (e.code() != 404 && e.code() != 405 && e.code() != 501) throw e
            Log.d("Outbox", "PATCH /meta unsupported (${e.code()}), trying PUT /meta…")
        }

        // 2) PUT /meta
        try {
            return@run projectsApi.updateProjectMetaPutLegacy(projectId, body)
        } catch (e: HttpException) {
            if (e.code() != 404 && e.code() != 405 && e.code() != 501) throw e
            Log.d("Outbox", "PUT /meta unsupported (${e.code()}), trying PUT /{id}…")
        }

        // 3) PUT /{id}
        projectsApi.updateProjectPutRootLegacy(projectId, body)
    }

    /** Собираем batch ИЗ ВЫБРАННЫХ bucket-ов одного проекта (с дедупликацией) */
    private suspend fun buildBatchSelective(
        projectId: String,
        items: List<OutboxEntity>,
        includeRooms: Boolean,
        includeDevices: Boolean
    ): ProjectBatchRequest {
        val hasProjectDelete = items.any { it.op_type == OutboxOpType.PROJECT_DELETE }
        if (hasProjectDelete) {
            // при удалении проекта мы ничего другого не отправляем
            return ProjectBatchRequest(baseVersion = null, ops = null)
        }

        // --- ROOMS: last-write-wins на уровне uuid ---
        val roomUpById = LinkedHashMap<String, RoomUpsert>()    // uuid -> upsert
        val roomDelIds = LinkedHashSet<String>()                 // set(uuid)

        // --- DEVICES: last-write-wins по uuid; UUID гарантируем здесь же ---
        val deviceUpById = LinkedHashMap<String, DeviceUpsert>() // uuid -> upsert
        val deviceDelIds = LinkedHashSet<String>()               // set(uuid)

        for (it in items) {
            when (it.op_type) {
                // ---------------- Rooms ----------------
                OutboxOpType.ROOM_CREATE, OutboxOpType.ROOM_UPDATE, OutboxOpType.ROOM_DELETE -> {
                    if (!includeRooms) continue
                    when (it.op_type) {
                        OutboxOpType.ROOM_CREATE -> {
                            val p = gson.fromJson(it.payload_json, RoomCreatePayload::class.java)
                            val name = p.name
                            val existingUuid = uuidDao.getRoomUuidByLocal(p.localId)
                            val uuid = existingUuid ?: UUID.randomUUID().toString().also {
                                uuidDao.putRooms(listOf(UuidMapRoom(roomUuid = it, localId = p.localId)))
                            }

                            val up = RoomUpsert(
                                id = uuid,
                                name = name,
                                meta = mapOf(
                                    "room_type" to p.roomType.name,
                                    "created_at_iso" to iso(Date(p.createdAt))
                                )
                            )
                            roomDelIds.remove(uuid)
                            roomUpById[uuid] = up
                        }

                        OutboxOpType.ROOM_UPDATE -> {
                            val p = gson.fromJson(it.payload_json, RoomUpdatePayload::class.java)
                            val uuid = uuidDao.getRoomUuidByLocal(p.localId)
                                ?: UUID.randomUUID().toString().also {
                                    uuidDao.putRooms(listOf(UuidMapRoom(roomUuid = it, localId = p.localId)))
                                }

                            val up = RoomUpsert(
                                id = uuid,
                                name = p.name,
                                meta = mapOf("room_type" to p.roomType.name)
                            )
                            roomDelIds.remove(uuid)
                            roomUpById[uuid] = up
                        }

                        OutboxOpType.ROOM_DELETE -> {
                            val p = gson.fromJson(it.payload_json, RoomDeletePayload::class.java)
                            val uuid = p.serverUuid ?: p.localId?.let { lid -> uuidDao.getRoomUuidByLocal(lid) }
                            if (uuid != null) {
                                roomUpById.remove(uuid)
                                roomDelIds.add(uuid)
                            }
                        }

                        else -> Unit
                    }
                }

                // ---------------- Devices ----------------
                OutboxOpType.DEVICE_CREATE, OutboxOpType.DEVICE_UPDATE, OutboxOpType.DEVICE_DELETE -> {
                    if (!includeDevices) continue
                    when (it.op_type) {
                        OutboxOpType.DEVICE_CREATE -> {
                            val p = gson.fromJson(it.payload_json, DeviceCreatePayload::class.java)
                            var deviceUuid = uuidDao.getDeviceUuidByLocal(p.localId)
                            if (deviceUuid == null) {
                                deviceUuid = UUID.randomUUID().toString()
                                uuidDao.putDevices(listOf(UuidMapDevice(deviceUuid = deviceUuid, localId = p.localId)))
                                Log.i("Outbox", "assigned new device UUID=$deviceUuid for local=${p.localId}")
                            }

                            val roomUuid = uuidDao.getRoomUuidByLocal(p.roomLocalId)
                            if (roomUuid == null) {
                                Log.w("Outbox", "device CREATE skip: room has no uuid yet (roomLocal=${p.roomLocalId})")
                                continue
                            }

                            val up = DeviceUpsert(
                                id = deviceUuid, // теперь всегда есть
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
                            deviceDelIds.remove(deviceUuid)
                            deviceUpById[deviceUuid] = up
                        }

                        OutboxOpType.DEVICE_UPDATE -> {
                            val p = gson.fromJson(it.payload_json, DeviceUpdatePayload::class.java)
                            var deviceUuid = uuidDao.getDeviceUuidByLocal(p.localId)
                            if (deviceUuid == null) {
                                // если девайсу ещё не назначали UUID — назначим сейчас
                                deviceUuid = UUID.randomUUID().toString()
                                uuidDao.putDevices(listOf(UuidMapDevice(deviceUuid = deviceUuid, localId = p.localId)))
                                Log.i("Outbox", "assigned new device UUID(on UPDATE)=$deviceUuid for local=${p.localId}")
                            }

                            val roomLocal = p.roomLocalId
                            val roomUuid = roomLocal?.let { uuidDao.getRoomUuidByLocal(it) }

                            val up = DeviceUpsert(
                                id = deviceUuid,
                                group_id = null,
                                name = p.name,
                                meta = deviceMeta(
                                    roomUuid = roomUuid,
                                    power = p.power,
                                    voltage = p.voltage,
                                    demandRatio = p.demandRatio,
                                    createdAt = Date(), // обновление — текущее время
                                    deviceType = p.deviceType,
                                    powerFactor = p.powerFactor,
                                    hasMotor = p.hasMotor,
                                    requiresDedicated = p.requiresDedicatedCircuit,
                                    requiresSocket = p.requiresSocketConnection
                                )
                            )
                            deviceDelIds.remove(deviceUuid)
                            deviceUpById[deviceUuid] = up
                        }

                        OutboxOpType.DEVICE_DELETE -> {
                            val p = gson.fromJson(it.payload_json, DeviceDeletePayload::class.java)
                            val uuid = p.serverUuid ?: p.localId?.let { lid -> uuidDao.getDeviceUuidByLocal(lid) }
                            if (uuid != null) {
                                deviceUpById.remove(uuid)
                                deviceDelIds.add(uuid)
                            }
                        }

                        else -> Unit
                    }
                }

                // Projects/Groups — не формируем batch; PROJECT_CREATE/UPDATE/DELETE обрабатываются вне batch.
                OutboxOpType.PROJECT_CREATE,
                OutboxOpType.PROJECT_UPDATE,
                OutboxOpType.PROJECT_DELETE,
                OutboxOpType.GROUP_CREATE,
                OutboxOpType.GROUP_UPDATE,
                OutboxOpType.GROUP_DELETE -> Unit
            }
        }

        // Собираем buckets
        val roomsBucket =
            if (includeRooms && (roomUpById.isNotEmpty() || roomDelIds.isNotEmpty()))
                OpBucket(upsert = roomUpById.values.toList(), delete = roomDelIds.toList())
            else null

        val deviceUps: List<DeviceUpsert> = deviceUpById.values.toList()
        val devicesBucket =
            if (includeDevices && (deviceUps.isNotEmpty() || deviceDelIds.isNotEmpty()))
                OpBucket(upsert = deviceUps, delete = deviceDelIds.toList())
            else null

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

        // если в комнате несколько девайсов с таким именем — лучше пропустить авто-маппинг,
        // чтобы не назначить UUID "не тому". Для этого метод findByRoomAndName должен возвращать
        // либо ровно один, либо null. Если сейчас он гарантирует ровно один — оставь как было.
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
        "voltage_type" to deviceType.voltageTypeNameFallback(voltage),
        "demand_ratio" to demandRatio,
        "created_at_iso" to iso(createdAt),
        "device_type" to deviceType.name,
        "power_factor" to powerFactor,
        "has_motor" to hasMotor,
        "requires_dedicated" to requiresDedicated,
        "requires_socket" to requiresSocket
    )
}

/** Небольшой хелпер — можно убрать, если не нужен специфичный тип в meta */
private fun DeviceType.voltageTypeNameFallback(v: Voltage): String = v.type.name

data class PushStats(
    val total: Int,
    val done: Int,
    val failed: Int
)

private fun isRetryableError(t: Throwable): Boolean {
    if (t is java.net.ConnectException ||
        t is java.net.UnknownHostException ||
        t is java.net.SocketTimeoutException ||
        t is javax.net.ssl.SSLException
    ) return true

    if (t is HttpException) {
        val code = t.code()
        if (code == 408 || code == 409 || code == 425 || code == 429) return true
        if (code in 500..599) return true
        return false
    }

    val msg = t.message?.lowercase().orEmpty()
    return !(msg.contains("400") || msg.contains("401") || msg.contains("403") || msg.contains("404"))
}