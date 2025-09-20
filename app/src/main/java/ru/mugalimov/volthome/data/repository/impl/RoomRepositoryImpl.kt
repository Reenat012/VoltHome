package ru.mugalimov.volthome.data.repository.impl

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.core.error.RoomAlreadyExistsException
import ru.mugalimov.volthome.core.error.RoomNotFoundException
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.local.dao.RoomsTxDao
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
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
import kotlinx.coroutines.flow.first

class RoomRepositoryImpl @Inject constructor(
    private val roomDao: RoomDao,
    private val deviceDao: DeviceDao,
    private val loadDao: LoadDao,
    private val explicationRepository: ExplicationRepository,
    private val roomsTxDao: RoomsTxDao,
    private val deviceDefaults: DeviceDefaultsProvider,
    private val activeProjectDs: ActiveProjectDataStore,
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : RoomRepository {

    /** Проектно-осознанное наблюдение: если активный проект выбран — фильтруем по нему. */
    override fun observeRooms(): Flow<List<Room>> {
        return activeProjectDs.activeProjectId
            .flatMapLatest { projectId ->
                if (projectId != null) roomDao.observeAllRoomsByProject(projectId)
                else roomDao.observeAllRooms() // безопасный фоллбэк до первичного выбора
            }
            .map { entities -> entities.mapToDomainRooms() }
            .flowOn(dispatchers)
    }

    override suspend fun addRoom(room: Room) {
        withContext(dispatchers) {
            if (roomDao.existsByName(room.name)) {
                throw RoomAlreadyExistsException("Комната с именем '${room.name}' уже существует")
            }
            val projectId = activeProjectDs.activeProjectId.first()

            val newRoomId = roomDao.addRoom(
                RoomEntity(
                    name = room.name,
                    createdAt = Date(),
                    roomType = room.roomType,
                    projectId = projectId
                )
            )
            // Создаём запись нагрузки комнаты в рамках того же проекта
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
        }
    }

    override suspend fun updateRoom(room: Room) {
        withContext(dispatchers) {
            try {
                val roomEntity = room.toEntityRoom()
                // сохраняем projectId, если он был в БД (toEntityRoom мог не содержать)
                val current = roomDao.getRoomById(room.id)
                roomDao.updateRoom(roomEntity.copy(projectId = current?.projectId))
            } catch (_: Exception) {
                throw RoomNotFoundException()
            }
        }
    }

    override suspend fun deleteRoom(roomId: Long) {
        withContext(dispatchers) {
            val rowsDeleted = roomDao.deleteRoomById(roomId)
            if (rowsDeleted == 0) throw RoomNotFoundException("Комната с ID $roomId не найдена")
            explicationRepository.handleRoomDeletion(roomId)
        }
    }

    override suspend fun getRoomById(roomId: Long): Room? = withContext(dispatchers) {
        try {
            val entity = roomDao.getRoomWithDevicesById(roomId)
            entity?.toDomainModelGroup()
        } catch (_: Exception) {
            throw RoomNotFoundException()
        }
    }

    /** Для виджета “Нагрузки” оставляем текущую реализацию; фильтрацию по проекту добавим позже при необходимости. */
    override suspend fun getRoomsWithLoads(): Flow<List<RoomWithLoad>> {
        return loadDao.getRoomsWithLoads()
            .map { list -> list.map { RoomWithLoad(room = it.room, load = it.load) } }
            .flowOn(dispatchers)
    }

    override suspend fun getRoomsWithDevices(): List<RoomWithDevice> =
        withContext(dispatchers) {
            val projectId = activeProjectDs.activeProjectId.first()
            if (projectId != null) {
                roomDao.observeAllRoomsWithDevicesByProject(projectId)
                    .toDomainModelListRoomWithDevices()
            } else {
                roomDao.observeAllRoomsWithDevices()
                    .toDomainModelListRoomWithDevices()
            }
        }

    override suspend fun getDefaultRooms(): Flow<List<DefaultRoom>> = JsonParser.parseRooms(context)

    override suspend fun getAllRoom(): List<Room> =
        withContext(dispatchers) { roomDao.getAllRooms().mapToDomainRooms() }

    override suspend fun addRoomWithDevices(req: RoomCreateRequest): CreatedRoomResult =
        withContext(dispatchers) {
            if (roomDao.existsByName(req.name)) {
                throw IllegalArgumentException("Комната '${req.name}' уже существует")
            }
            val projectId = activeProjectDs.activeProjectId.first()

            val room = RoomEntity(
                id = 0L,
                name = req.name,
                roomType = req.roomType,
                createdAt = Date(),
                projectId = projectId
            )

            // Заготовим устройства (roomId появится в транзакции), но уже с projectId
            val devices = expand(req.devices, roomId = null, projectId = projectId)

            val (roomId, deviceIds) = roomsTxDao.insertRoomWithDevices(room, devices)

            // создаём запись нагрузки под проект
            loadDao.addLoad(
                LoadEntity(
                    name = req.name,
                    currentRoom = 0.0,
                    powerRoom = 0,
                    countDevices = 0,
                    createdAt = Date(),
                    roomId = roomId,
                    projectId = projectId
                )
            )

            CreatedRoomResult(roomId = roomId, deviceIds = deviceIds)
        }

    override suspend fun addDevicesToRoom(
        roomId: Long,
        devices: List<DeviceCreateRequest>
    ): List<Long> = withContext(dispatchers) {
        val room = roomDao.getRoomById(roomId)
            ?: throw RoomNotFoundException("Комната $roomId не найдена")
        val entities = expand(devices, roomId = roomId, projectId = room.projectId)
        roomsTxDao.insertDevices(entities)
    }

    override suspend fun deleteDevices(deviceIds: List<Long>) = withContext(dispatchers) {
        if (deviceIds.isNotEmpty()) roomsTxDao.deleteDevicesByIds(deviceIds)
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