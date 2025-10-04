package ru.mugalimov.volthome.data.repository.impl

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
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
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.data.remote.dto.OpBucket
import ru.mugalimov.volthome.data.remote.dto.Ops
import ru.mugalimov.volthome.data.remote.dto.ProjectBatchRequest
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.data.sync.work.SyncProjectsWorker
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.mapper.mapToDomainRooms
import ru.mugalimov.volthome.domain.mapper.toDomainModelGroup
import ru.mugalimov.volthome.domain.mapper.toDomainModelListRoomWithDevices
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
    private val projectsApi: ProjectsApi,
    private val appDb: AppDatabase
) : RoomRepository {

    private val uuidDao get() = appDb.uuidMapDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRooms(): Flow<List<Room>> {
        return activeProjectDs.activeProjectId
            .flatMapLatest { projectId ->
                if (projectId.isNullOrBlank()) {
                    flowOf(emptyList())
                } else {
                    roomDao.observeAllRoomsByProject(projectId)
                }
            }
            .map { entities -> entities.mapToDomainRooms() }
            .flowOn(dispatchers)
    }

    override suspend fun addRoom(room: Room) {
        withContext(dispatchers) {
            val projectId = projectsRepo.ensureActiveDraft()
            Log.i("AddRoom", "CLICK name='${room.name}' projectId=$projectId")

            val exists = roomDao.existsByNameInProject(room.name, projectId)
            if (exists) throw RoomAlreadyExistsException("Комната '${room.name}' уже существует")

            val newRoomId = roomDao.addRoom(
                RoomEntity(
                    name = room.name,
                    createdAt = Date(),
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
                    createdAt = Date(),
                    roomId = newRoomId,
                    projectId = projectId
                )
            )

            SyncProjectsWorker.enqueue(context, projectId)
        }
    }

    override suspend fun updateRoom(room: Room) {
        withContext(dispatchers) {
            val current = roomDao.getRoomById(room.id) ?: throw RoomNotFoundException()
            val roomEntity = room.toEntityRoom().copy(projectId = current.projectId)
            roomDao.updateRoom(roomEntity)
            current.projectId?.let { SyncProjectsWorker.enqueue(context, it) }
        }
    }

    override suspend fun deleteRoom(roomId: Long) {
        withContext(dispatchers) {
            val current = roomDao.getRoomById(roomId)
                ?: throw RoomNotFoundException("Комната $roomId не найдена")
            val projectId = current.projectId
            val isDraft = projectId.isNullOrBlank() || projectId.startsWith("draft-")

            // список device UUID для каскада на сервере
            val devicesInRoom: List<DeviceEntity> = deviceDao.getAllDevicesByRoomId(roomId)
            val deviceUuids = devicesInRoom.mapNotNull { uuidDao.getDeviceUuidByLocal(it.deviceId) }
            val roomUuid = uuidDao.getRoomUuidByLocal(roomId)

            if (!isDraft && (roomUuid != null || deviceUuids.isNotEmpty())) {
                // 1) Удаляем НА СЕРВЕРЕ (атомарно через batch). Если не получилось — не трогаем локально.
                val ops = Ops(
                    rooms   = roomUuid?.let { OpBucket(upsert = emptyList(), delete = listOf(it)) },
                    groups  = null,
                    devices = if (deviceUuids.isNotEmpty())
                        OpBucket(upsert = emptyList(), delete = deviceUuids)
                    else null
                )
                try {
                    projectsApi.applyBatch(projectId!!, ProjectBatchRequest(baseVersion = null, ops = ops))
                    Log.i("RoomDelete", "Server delete ok: room=$roomUuid dev=${deviceUuids.size} project=$projectId")
                } catch (t: Throwable) {
                    Log.w("RoomDelete", "Server delete failed, keep local. reason=${t.message}", t)
                    // важный момент: НЕ удаляем локально, иначе они вернутся со снапшотом
                    throw t
                }
            }

            // 2) Локально удаляем комнату и производные
            val rowsDeleted = roomDao.deleteRoomById(roomId)
            if (rowsDeleted == 0) throw RoomNotFoundException("Комната $roomId не найдена")
            explicationRepository.handleRoomDeletion(roomId)

            projectId?.let { SyncProjectsWorker.enqueue(context, it) }
        }
    }

    override suspend fun getRoomById(roomId: Long): Room? = withContext(dispatchers) {
        val entity = roomDao.getRoomWithDevicesById(roomId) ?: return@withContext null
        entity.toDomainModelGroup()
    }

    override suspend fun getRoomsWithLoads(): Flow<List<RoomWithLoad>> {
        return loadDao.getRoomsWithLoads()
            .map { list -> list.map { RoomWithLoad(room = it.room, load = it.load) } }
            .flowOn(dispatchers)
    }

    override suspend fun getRoomsWithDevices(): List<RoomWithDevice> =
        withContext(dispatchers) {
            val projectId = activeProjectDs.activeProjectId.first()
            if (!projectId.isNullOrBlank()) {
                roomDao.observeAllRoomsWithDevicesByProject(projectId)
                    .toDomainModelListRoomWithDevices()
            } else {
                emptyList()
            }
        }

    override suspend fun getDefaultRooms(): Flow<List<DefaultRoom>> = JsonParser.parseRooms(context)

    override suspend fun getAllRoom(): List<Room> =
        withContext(dispatchers) { roomDao.getAllRooms().mapToDomainRooms() }

    override suspend fun addRoomWithDevices(req: RoomCreateRequest): CreatedRoomResult =
        withContext(dispatchers) {
            val projectId = projectsRepo.ensureActiveDraft()
            Log.i("AddRoomWD", "CLICK name='${req.name}' projectId=$projectId")

            val exists = roomDao.existsByNameInProject(req.name, projectId)
            if (exists) throw IllegalArgumentException("Комната '${req.name}' уже существует")

            val room = RoomEntity(
                id = 0L, name = req.name, roomType = req.roomType, createdAt = Date(), projectId = projectId
            )

            val devices = expand(req.devices, roomId = null, projectId = projectId)
            val (roomId, deviceIds) = roomsTxDao.insertRoomWithDevices(room, devices)

            loadDao.addLoad(
                LoadEntity(
                    name = req.name, currentRoom = 0.0, powerRoom = 0,
                    countDevices = 0, createdAt = Date(), roomId = roomId, projectId = projectId
                )
            )

            SyncProjectsWorker.enqueue(context, projectId)
            CreatedRoomResult(roomId = roomId, deviceIds = deviceIds)
        }

    override suspend fun addDevicesToRoom(
        roomId: Long,
        devices: List<DeviceCreateRequest>
    ): List<Long> = withContext(dispatchers) {
        val room = roomDao.getRoomById(roomId)
            ?: throw RoomNotFoundException("Комната $roomId не найдена")
        val projectId = room.projectId
        val entities = expand(devices, roomId = roomId, projectId = projectId)
        val ids = roomsTxDao.insertDevices(entities)
        projectId?.let { SyncProjectsWorker.enqueue(context, it) }
        ids
    }

    override suspend fun deleteDevices(deviceIds: List<Long>) = withContext(dispatchers) {
        if (deviceIds.isEmpty()) return@withContext

        val anyDevice = deviceDao.getDeviceById(deviceIds.first().toInt()) ?: return@withContext
        val projectId = anyDevice.projectId
        val isDraft = projectId.isNullOrBlank() || projectId.startsWith("draft-")

        if (!isDraft) {
            val uuidsToDelete = deviceIds.mapNotNull { id -> uuidDao.getDeviceUuidByLocal(id) }
            if (uuidsToDelete.isNotEmpty()) {
                try {
                    val ops = Ops(
                        rooms = null, groups = null,
                        devices = OpBucket(upsert = emptyList(), delete = uuidsToDelete)
                    )
                    projectsApi.applyBatch(projectId!!, ProjectBatchRequest(baseVersion = null, ops = ops))
                    Log.i("DeviceDelete", "Server delete ok: count=${uuidsToDelete.size} project=$projectId")
                } catch (t: Throwable) {
                    Log.w("DeviceDelete", "Server delete failed, keep local. reason=${t.message}", t)
                    throw t
                }
            }
        }

        roomsTxDao.deleteDevicesByIds(deviceIds)
        projectId?.let { SyncProjectsWorker.enqueue(context, it) }
    }

    private fun expand(
        reqs: List<DeviceCreateRequest>,
        roomId: Long? = null,
        projectId: String?
    ): List<DeviceEntity> = reqs.flatMap { r ->
        val def = deviceDefaults[r.type]
        val qty = r.count.coerceAtLeast(0)
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
                hasMotor = def.hasMotor,
                requiresDedicatedCircuit = def.requiresDedicatedCircuit,
                requiresSocketConnection = def.requiresSocketConnection,
                projectId = projectId
            )
        }
    }
}