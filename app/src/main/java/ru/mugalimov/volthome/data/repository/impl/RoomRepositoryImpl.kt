package ru.mugalimov.volthome.data.repository.impl

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import ru.mugalimov.volthome.core.error.RoomAlreadyExistsException
import ru.mugalimov.volthome.core.error.RoomNotFoundException
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.dao.RoomsTxDao
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.mapper.mapToDomainDevices
import ru.mugalimov.volthome.domain.mapper.mapToDomainRooms
import ru.mugalimov.volthome.domain.mapper.toEntityRoom
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomWithDevice
import ru.mugalimov.volthome.domain.model.RoomWithLoad
import ru.mugalimov.volthome.domain.model.create.CreatedRoomResult
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest
import ru.mugalimov.volthome.domain.model.provider.DeviceDefaultsProvider
import ru.mugalimov.volthome.ui.components.JsonParser
import java.util.Date
import javax.inject.Inject

// outbox / tombstones
import ru.mugalimov.volthome.data.local.dao.OutboxDao
import ru.mugalimov.volthome.data.local.dao.TombstoneDao
import ru.mugalimov.volthome.data.local.entity.OutboxEntity
import ru.mugalimov.volthome.data.local.entity.OutboxOpType
import ru.mugalimov.volthome.data.local.entity.TombstoneEntity
import ru.mugalimov.volthome.data.local.entity.TombstoneEntityType
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.data.sync.outbox.RoomCreatePayload
import ru.mugalimov.volthome.data.sync.outbox.RoomUpdatePayload
import ru.mugalimov.volthome.data.sync.outbox.RoomDeletePayload
import ru.mugalimov.volthome.data.sync.outbox.DeviceCreatePayload
import ru.mugalimov.volthome.data.sync.outbox.DeviceDeletePayload
import ru.mugalimov.volthome.data.sync.outbox.OutboxPushWorker
import ru.mugalimov.volthome.data.sync.outbox.toJson
import ru.mugalimov.volthome.domain.model.DevicePreview
import ru.mugalimov.volthome.domain.model.RoomWithDevicesPreview
import ru.mugalimov.volthome.domain.telemetry.CreateDeviceOpBus
import ru.mugalimov.volthome.domain.telemetry.CreateDeviceOpEvent
import ru.mugalimov.volthome.domain.use_case.AutoRebuildGroupsAfterDeviceInsertUseCase
import dagger.Lazy
import java.util.UUID

class RoomRepositoryImpl @Inject constructor(
    private val roomDao: RoomDao,
    private val deviceDao: DeviceDao,
    private val loadDao: LoadDao,
    private val explicationRepository: ExplicationRepository,
    private val roomsTxDao: RoomsTxDao,
    private val deviceDefaults: DeviceDefaultsProvider,
    private val activeProjectDs: ActiveProjectDataStore,
    private val projectsRepo: ProjectsRepository,
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
    private val appDb: AppDatabase,
    private val outboxDao: OutboxDao,
    private val tombstoneDao: TombstoneDao,
    // ✅ Commit 1: bus корреляции add-devices
    private val createDeviceOpBus: CreateDeviceOpBus,
    private val autoRebuildAfterInsertUseCase: Lazy<AutoRebuildGroupsAfterDeviceInsertUseCase>,
    private val manualRepo:
    ManualEditSessionRepository,
) : RoomRepository {

    private val uuidDao get() = appDb.uuidMapDao()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeRooms(): Flow<List<Room>> {
        return activeProjectDs.activeProjectId
            .distinctUntilChanged() // ✅ не залипаем/не пересобираем один и тот же проект
            .flatMapLatest { projectId ->
                if (projectId.isNullOrBlank()) flowOf(emptyList())
                else roomDao.observeAllRoomsByProject(projectId)
            }
            .map { entities -> entities.mapToDomainRooms() }
            .flowOn(dispatchers)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeRoomsWithDevicesPreview(): Flow<List<RoomWithDevicesPreview>> {
        return activeProjectDs.activeProjectId
            .distinctUntilChanged() // ✅ переключение проекта = новый flow, тот же проект = без дубликатов
            .flatMapLatest { projectId ->
                if (projectId.isNullOrBlank()) flowOf(emptyList())
                else roomDao.observeRoomsWithDevicesPreviewByProject(projectId)
            }
            .map { rows ->
                rows.map { r ->
                    val names = listOfNotNull(r.previewName1, r.previewName2, r.previewName3)

                    RoomWithDevicesPreview(
                        roomId = r.roomId,
                        name = r.roomName,
                        roomType = r.roomType,
                        devicesCount = r.devicesCount,
                        devicesPreview = names.mapIndexed { idx, name ->
                            DevicePreview(
                                id = -1L - idx,
                                name = name
                            )
                        }
                    )
                }
            }
            .flowOn(dispatchers)
    }

    override suspend fun addRoom(room: Room) {
        withContext(dispatchers) {
            val projectId = projectsRepo.ensureActiveDraft()
            Log.i("AddRoom", "CLICK name='${room.name}' projectId=$projectId")

            val exists = roomDao.existsByNameInProject(room.name, projectId)
            if (exists) throw RoomAlreadyExistsException("Комната '${room.name}' уже существует")

            val createdAt = Date()
            val newRoomId = roomDao.addRoom(
                RoomEntity(
                    name = room.name,
                    createdAt = createdAt,
                    roomType = room.roomType,
                    projectId = projectId
                )
            )

            loadDao.addLoad(
                LoadEntity(
                    name = room.name,
                    currentRoom = 0.0,
                    powerRoom = 0,
                    countDevices = 0,
                    createdAt = createdAt,
                    roomId = newRoomId,
                    projectId = projectId
                )
            )

            // outbox ROOM_CREATE
            outboxDao.insert(
                OutboxEntity(
                    project_id = projectId,
                    op_type = OutboxOpType.ROOM_CREATE,
                    payload_json = RoomCreatePayload(
                        projectId = projectId,
                        localId = newRoomId,
                        name = room.name,
                        roomType = room.roomType,
                        createdAt = createdAt.time
                    ).toJson(),
                    group_key = "room:create:$projectId:$newRoomId"
                )
            )

            OutboxPushWorker.enqueueProject(context, projectId)
        }
    }

    override suspend fun updateRoom(room: Room) {
        withContext(dispatchers) {
            val current = roomDao.getRoomById(room.id) ?: throw RoomNotFoundException()
            val roomEntity = room.toEntityRoom().copy(projectId = current.projectId)
            roomDao.updateRoom(roomEntity)

            current.projectId?.let { pid ->
                outboxDao.insert(
                    OutboxEntity(
                        project_id = pid,
                        op_type = OutboxOpType.ROOM_UPDATE,
                        payload_json = RoomUpdatePayload(
                            projectId = pid,
                            localId = room.id,
                            name = room.name,
                            roomType = room.roomType
                        ).toJson(),
                        group_key = "room:update:$pid:${room.id}"
                    )
                )
                OutboxPushWorker.enqueueProject(context, pid)
            }
        }
    }

    /**
     * ФАЗА 3: мягкое каскадное удаление комнаты без изменения схемы Room.
     * Порядок:
     *  1) Удаляем устройства комнаты через существующий deleteDevices(...) — это ставит tombstones и DEVICE_DELETE в outbox.
     *  2) Чистим группы/связи комнаты (ExplicationRepository.handleRoomDeletion).
     *  3) Ставим tombstone на комнату и пишем ROOM_DELETE в outbox.
     *  4) Физически удаляем комнату локально.
     *  5) Тригерим OutboxPush.
     *
     * Такой порядок гарантирует, что сначала в outbox уйдут device.delete, затем room.delete.
     */
    override suspend fun deleteRoom(roomId: Long) {
        withContext(dispatchers) {
            val current = roomDao.getRoomById(roomId)
                ?: throw RoomNotFoundException("Комната $roomId не найдена")
            val projectId = current.projectId
                ?: throw RoomNotFoundException("У комнаты нет projectId")

            // 1) Собираем устройства комнаты и удаляем их через уже существующую реализацию
            val devicesInRoom: List<DeviceEntity> = deviceDao.getAllDevicesByRoomId(roomId)
            if (devicesInRoom.isNotEmpty()) {
                val deviceIds = devicesInRoom.map { it.deviceId }
                // reuse готовую логику: она ставит tombstones + outbox DEVICE_DELETE и удаляет локально
                deleteDevices(deviceIds)
            }

            // 2) Чистим группы и зависимости комнаты (локально). Сервер удалит каскадно при ROOM_DELETE.
            explicationRepository.handleRoomDeletion(roomId)

            // 3) Tombstone + outbox для комнаты (после девайсов)
            tombstoneDao.insert(
                TombstoneEntity(
                    project_id = projectId,
                    entity_type = TombstoneEntityType.ROOM,
                    local_id = roomId,
                    server_uuid = uuidDao.getRoomUuidByLocal(roomId)
                )
            )
            outboxDao.insert(
                OutboxEntity(
                    project_id = projectId,
                    op_type = OutboxOpType.ROOM_DELETE,
                    payload_json = RoomDeletePayload(
                        projectId = projectId,
                        localId = roomId,
                        serverUuid = uuidDao.getRoomUuidByLocal(roomId)
                    ).toJson(),
                    group_key = "room:delete:$projectId:$roomId"
                )
            )

            // 4) Физически удаляем комнату
            val rowsDeleted = roomDao.deleteRoomById(roomId)
            if (rowsDeleted == 0) throw RoomNotFoundException("Комната $roomId не найдена")

            // 5) Пуш
            OutboxPushWorker.enqueueProject(context, projectId)
        }
    }

    override suspend fun getRoomById(roomId: Long): Room? = withContext(dispatchers) {
        val entity = roomDao.getRoomById(roomId) ?: return@withContext null
        listOf(entity).mapToDomainRooms().firstOrNull()
    }

    override suspend fun getRoomsWithLoads(): Flow<List<RoomWithLoad>> {
        return loadDao.getRoomsWithLoads()
            .map { list -> list.map { RoomWithLoad(room = it.room, load = it.load) } }
            .flowOn(dispatchers)
    }

    override suspend fun getRoomsWithDevices(): List<RoomWithDevice> =
        withContext(dispatchers) {
            val projectId = activeProjectDs.activeProjectId.first()
            if (projectId.isNullOrBlank()) return@withContext emptyList()

            val result = mutableListOf<RoomWithDevice>()
            val roomEntities = roomDao.getAllRoomsByProject(projectId)
            for (roomEntity in roomEntities) {
                val devEntities = deviceDao.getAllDevicesByRoomId(roomEntity.id)
                result += RoomWithDevice(room = roomEntity, devices = devEntities)
            }
            result
        }

    override suspend fun getDefaultRooms(): Flow<List<DefaultRoom>> =
        JsonParser.parseRooms(context)

    override suspend fun getAllRoom(): List<Room> =
        withContext(dispatchers) { roomDao.getAllRooms().mapToDomainRooms() }

    override suspend fun addRoomWithDevices(req: RoomCreateRequest): CreatedRoomResult {
        // ✅ Commit 2: старый контракт не ломаем — генерируем legacy-opId и делегируем в новый overload.
        val opId = "legacy-" + UUID.randomUUID().toString()
        return addRoomWithDevices(req = req, opId = opId)
    }

    override suspend fun addRoomWithDevices(
        req: RoomCreateRequest,
        opId: String
    ): CreatedRoomResult = withContext(dispatchers) {
        val projectId = projectsRepo.ensureActiveDraft()

        // ✅ Commit 2: логируем opId прямо в repo (как требование gates)
        Log.i(
            "AddRoomWD",
            "CLICK name='${req.name}' projectId=$projectId opId=$opId"
        )

        val exists = roomDao.existsByNameInProject(req.name, projectId)
        if (exists) throw IllegalArgumentException("Комната '${req.name}' уже существует")

        // ✅ Commit 3: tombstone-consistent count ДО (как в add-devices)
        val countBefore = deviceDao.countActiveByProjectId(projectId)

        val createdAt = Date()
        val room = RoomEntity(
            id = 0L,
            name = req.name,
            roomType = req.roomType,
            createdAt = createdAt,
            projectId = projectId
        )

        val devices = expand(req.devices, roomId = null, projectId = projectId)
        val (roomId, deviceIds) = roomsTxDao.insertRoomWithDevices(room, devices)

        // ✅ Commit 3: tombstone-consistent count ПОСЛЕ
        val countAfter = deviceDao.countActiveByProjectId(projectId)
        val delta = countAfter - countBefore

        // ✅ Commit 3: единый CREATE_DEVICE_DB лог (формат один-в-один с add-devices)
        // ВАЖНО: room-create вставляет устройства "внутри" room TX, но лог должен выглядеть одинаково.
        Log.i(
            "CREATE_DEVICE_DB",
            "thread=${Thread.currentThread().name} " +
                    "projectIdRecorded=$projectId roomId=$roomId " +
                    "insertedIds(size=${deviceIds.size})=${deviceIds.take(20)} " +
                    "countBefore=$countBefore countAfter=$countAfter delta=$delta " +
                    "opId=$opId"
        )

        // ✅ Commit 3: publish в CreateDeviceOpBus (чтобы UI мог коррелировать видимость)
        // Это "последний снимок операции", не бизнес-логика.
        createDeviceOpBus.publish(
            CreateDeviceOpEvent(
                opId = opId,
                projectIdRecorded = projectId,
                roomId = roomId,
                insertedIds = deviceIds
            )
        )

        // ✅ Commit 4: MANUAL post-insert для room-create (unassigned)
        // ВАЖНО:
        // 1) строго по projectIdRecorded (projectId из ensureActiveDraft), без activeProjectId и без VM
        // 2) это единственная точка, которая "автоматом" кладёт созданные устройства в draft.unassignedDeviceIds
        // 3) лог-гейт: должен быть ровно один на операцию (как и CREATE_DEVICE_DB)
        if (manualRepo.isManualActive(projectId)) {
            Log.i(
                "MANUAL_POST_INSERT",
                "pid=$projectId opId=$opId inserted=${deviceIds.size} roomId=$roomId"
            )

            manualRepo.addInsertedDevicesToUnassigned(
                projectId = projectId,
                insertedDeviceIds = deviceIds,
                opId = opId // ✅ корреляция с CREATE_DEVICE_DB / CreateDeviceOpBus
            )
        }

        loadDao.addLoad(
            LoadEntity(
                name = req.name,
                currentRoom = 0.0,
                powerRoom = 0,
                countDevices = 0,
                createdAt = createdAt,
                roomId = roomId,
                projectId = projectId
            )
        )

        // outbox ROOM_CREATE
        outboxDao.insert(
            OutboxEntity(
                project_id = projectId,
                op_type = OutboxOpType.ROOM_CREATE,
                payload_json = RoomCreatePayload(
                    projectId = projectId,
                    localId = roomId,
                    name = req.name,
                    roomType = req.roomType,
                    createdAt = createdAt.time
                ).toJson(),
                group_key = "room:create:$projectId:$roomId"
            )
        )

        // outbox DEVICE_CREATE для каждого устройства (сопоставляем ids с исходными entities)
        deviceIds.forEachIndexed { index, devId ->
            val dev = devices[index]
            outboxDao.insert(
                OutboxEntity(
                    project_id = projectId,
                    op_type = OutboxOpType.DEVICE_CREATE,
                    payload_json = DeviceCreatePayload(
                        projectId = projectId,
                        localId = devId,
                        roomLocalId = roomId,
                        name = dev.name,
                        power = dev.power,
                        voltage = dev.voltage,
                        demandRatio = dev.demandRatio,
                        createdAt = dev.createdAt.time,
                        deviceType = dev.deviceType,
                        powerFactor = dev.powerFactor,
                        hasMotor = dev.hasMotor,
                        requiresDedicatedCircuit = dev.requiresDedicatedCircuit,
                        requiresSocketConnection = dev.requiresSocketConnection
                    ).toJson(),
                    group_key = "device:create:$projectId:$devId"
                )
            )
        }

        OutboxPushWorker.enqueueProject(context, projectId)

        // ✅ Возвращаем результат как и раньше
        CreatedRoomResult(roomId = roomId, deviceIds = deviceIds)
    }

    override suspend fun addDevicesToRoom(
        roomId: Long,
        devices: List<DeviceCreateRequest>
    ): List<Long> {
        // ✅ Не ломаем старых вызывающих: генерируем opId внутри.
        // Это даст логи/корреляцию даже для legacy вызовов.
        val opId = "legacy-" + java.util.UUID.randomUUID().toString()
        return addDevicesToRoom(roomId = roomId, devices = devices, opId = opId)
    }

    override suspend fun addDevicesToRoom(
        roomId: Long,
        devices: List<DeviceCreateRequest>,
        opId: String
    ): List<Long> = withContext(dispatchers) {
        val room = roomDao.getRoomById(roomId)
            ?: throw RoomNotFoundException("Комната $roomId не найдена")
        val projectId = room.projectId
            ?: throw RoomNotFoundException("У комнаты нет projectId")

        // ✅ Commit 1: tombstone-consistent count ДО
        val countBefore = deviceDao.countActiveByProjectId(projectId)

        val entities = expand(devices, roomId = roomId, projectId = projectId)
        val ids = roomsTxDao.insertDevices(entities)

        // ✅ Commit 1: tombstone-consistent count ПОСЛЕ
        val countAfter = deviceDao.countActiveByProjectId(projectId)
        val delta = countAfter - countBefore

        // ✅ Commit 1: CREATE_DEVICE_DB лог (thread + projectIdRecorded + roomId + ids + delta + opId)
        Log.i(
            "CREATE_DEVICE_DB",
            "thread=${Thread.currentThread().name} " +
                    "projectIdRecorded=$projectId roomId=$roomId " +
                    "insertedIds(size=${ids.size})=${ids.take(20)} " +
                    "countBefore=$countBefore countAfter=$countAfter delta=$delta " +
                    "opId=$opId"
        )

        // ✅ Commit 1: эмитим событие для VM (тут есть факт projectIdRecorded)
        createDeviceOpBus.publish(
            CreateDeviceOpEvent(
                opId = opId,
                projectIdRecorded = projectId,
                roomId = roomId,
                insertedIds = ids
            )
        )

        // ✅ Commit 2: MANUAL последствия
        // ВАЖНО: строго по projectIdRecorded (room.projectId), без activeProjectId и без VM.
        if (manualRepo.isManualActive(projectId)) {
            manualRepo.addInsertedDevicesToUnassigned(
                projectId = projectId,
                insertedDeviceIds = ids,
                opId = opId // ✅ корреляция с CREATE_DEVICE_DB
            )
        }

        // ✅ Commit 3: AUTO rebuild после вставки (НЕ из UI)
        // A: emptyIds -> no-op, B: manual -> suppress, C: full rebuild, D: single-flight logs
        autoRebuildAfterInsertUseCase.get().execute(
            ru.mugalimov.volthome.domain.use_case.AutoRebuildGroupsAfterDeviceInsertUseCase.Params(
                projectIdRecorded = projectId,
                insertedIds = ids,
                opId = opId
            )
        )

        // outbox DEVICE_CREATE для каждого добавленного устройства
        ids.forEachIndexed { index, devId ->
            val dev = entities[index]
            outboxDao.insert(
                OutboxEntity(
                    project_id = projectId,
                    op_type = OutboxOpType.DEVICE_CREATE,
                    payload_json = DeviceCreatePayload(
                        projectId = projectId,
                        localId = devId,
                        roomLocalId = roomId,
                        name = dev.name,
                        power = dev.power,
                        voltage = dev.voltage,
                        demandRatio = dev.demandRatio,
                        createdAt = dev.createdAt.time,
                        deviceType = dev.deviceType,
                        powerFactor = dev.powerFactor,
                        hasMotor = dev.hasMotor,
                        requiresDedicatedCircuit = dev.requiresDedicatedCircuit,
                        requiresSocketConnection = dev.requiresSocketConnection
                    ).toJson(),
                    group_key = "device:create:$projectId:$devId"
                )
            )
        }

        OutboxPushWorker.enqueueProject(context, projectId)
        ids
    }

    override suspend fun deleteDevices(deviceIds: List<Long>) = withContext(dispatchers) {
        if (deviceIds.isEmpty()) return@withContext

        val anyDevice = deviceDao.getDeviceById(deviceIds.first().toInt()) ?: return@withContext
        val projectId = anyDevice.projectId ?: return@withContext

        // tombstones + outbox DEVICE_DELETE для каждого
        deviceIds.forEach { id ->
            val entity = deviceDao.getDeviceById(id.toInt()) ?: return@forEach
            tombstoneDao.insert(
                TombstoneEntity(
                    project_id = projectId,
                    entity_type = TombstoneEntityType.DEVICE,
                    local_id = id,
                    server_uuid = uuidDao.getDeviceUuidByLocal(id)
                )
            )
            outboxDao.insert(
                OutboxEntity(
                    project_id = projectId,
                    op_type = OutboxOpType.DEVICE_DELETE,
                    payload_json = DeviceDeletePayload(
                        projectId = projectId,
                        localId = id,
                        serverUuid = uuidDao.getDeviceUuidByLocal(id)
                    ).toJson(),
                    group_key = "device:delete:$projectId:$id"
                )
            )
        }

        roomsTxDao.deleteDevicesByIds(deviceIds)
        OutboxPushWorker.enqueueProject(context, projectId)
    }

    private fun expand(
        reqs: List<DeviceCreateRequest>,
        roomId: Long? = null,
        projectId: String?
    ): List<DeviceEntity> = reqs.flatMap { r ->
        val def = deviceDefaults[r.type]
        val qty = r.count.coerceAtLeast(0)

        // ✅ Commit 0: берём кастомные флаги из запроса, если они заданы в UI.
        // Если UI не задавал — используем дефолты из каталога (provider).
        val hasMotor = r.hasMotor ?: def.hasMotor
        val requiresDedicated = r.requiresDedicatedCircuit ?: def.requiresDedicatedCircuit
        val requiresSocket = r.requiresSocketConnection ?: def.requiresSocketConnection

        (0 until qty).map {
            DeviceEntity(
                deviceId = 0L,
                name = r.title,
                power = r.ratedPowerW ?: def.power,
                voltage = r.voltage ?: def.voltage,
                demandRatio = r.demandRatio ?: def.demandRatio,
                createdAt = Date(),
                roomId = roomId ?: 0L,
                deviceType = r.type,
                powerFactor = r.powerFactor ?: def.powerFactor,

                // ✅ Commit 0: НЕ игнорируем кастомизацию, которую ввёл пользователь в UI
                hasMotor = hasMotor,
                requiresDedicatedCircuit = requiresDedicated,
                requiresSocketConnection = requiresSocket,

                projectId = projectId
            )
        }
    }
}