package ru.mugalimov.volthome.data.repository.impl

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.core.error.RoomAlreadyExistsException
import ru.mugalimov.volthome.core.error.RoomNotFoundException
import ru.mugalimov.volthome.data.local.dao.RoomsTxDao
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.Load
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomWithLoad
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.RoomWithDevice
import ru.mugalimov.volthome.domain.model.RoomWithDevicesEntity
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
    //свойство dispatchers, которое хранит диспетчер для запуска корутин
    //в фоновых потоках, подходящих для IO-задач
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : RoomRepository {

    //получение списка комнат через DAO
    //используется для получения изменения данных
    override fun observeRooms(): Flow<List<Room>> {
        return roomDao.observeAllRooms()
            .map { entities -> entities.toDomainModelListRoom() }
            .flowOn(dispatchers)
    }


    override suspend fun addRoom(room: Room) {
        //запускаем в фоновом потоке, используя корутину
        withContext(dispatchers) {
            //проверка на существование комнаты по имени
            if (roomDao.existsByName(room.name)) {
                throw RoomAlreadyExistsException("Комната с именем '${room.name}' уже существует")
            }

            //добавляем комнату в БД
            roomDao.addRoom(
                RoomEntity(
                    name = room.name,
                    createdAt = Date(),
                    roomType = room.roomType
                )
            ).let { roomId ->
                loadDao.addLoad(LoadEntity(roomId = roomId, createdAt = Date()))
            }
        }
    }

    override suspend fun updateRoom(room: Room) {
        withContext(dispatchers) {
            try {
                val roomEntity = room.toDomainModelRoomEntity()

                roomDao.updateRoom(roomEntity)
            } catch (e: Exception) {
                throw RoomNotFoundException()
            }
        }
    }

    override suspend fun deleteRoom(roomId: Long) {
        //запускаем в фоновом потоке, используя корутину
        withContext(dispatchers) {
            //записываем в переменную число удаленных строк
            val rowsDeleted = roomDao.deleteRoomById(roomId)
            //проверяем удалилось что-нибудь или нет
            if (rowsDeleted == 0) {
                throw RoomNotFoundException("Комната с ID $roomId не найдена")
            }

            // Обработка удаления с комнатой связанных групп
            explicationRepository.handleRoomDeletion(roomId)
        }
    }

    override suspend fun getRoomById(roomId: Long): Room? = withContext(dispatchers) {
        return@withContext try {
            //ищем комнату в БД
            val entity = roomDao.getRoomWithDevicesById(roomId)

            //преобразовываем из Entity в модель удобную для чтения
            entity?.toDomainModelGroup()
        } catch (e: Exception) {
            throw RoomNotFoundException()
        }
    }

    override suspend fun getRoomsWithLoads(): Flow<List<RoomWithLoad>> {
        return loadDao.getRoomsWithLoads()
            .map { list ->
                list.map {
                    RoomWithLoad(
                        room = it.room,
                        load = it.load // Дефолтное значение
                    )
                }
            }
            .flowOn(dispatchers)
    }

    override suspend fun getRoomsWithDevices(): List<RoomWithDevice> =
        withContext(dispatchers) {
            return@withContext roomDao.observeAllRoomsWithDevices()
                .toDomainModelListRoomWithDevices()
        }

    override suspend fun getDefaultRooms(): Flow<List<DefaultRoom>> {
        return JsonParser.parseRooms(context)
    }

    override suspend fun getAllRoom(): List<Room> =
        withContext(dispatchers) {
            return@withContext try {
                roomDao.getAllRooms().toDomainModelListRoom()
            } catch (e: Exception) {
                throw RoomNotFoundException()
            }
        }

    override suspend fun addRoomWithDevices(req: RoomCreateRequest): CreatedRoomResult =
        withContext(dispatchers) {
            if (roomDao.existsByName(req.name)) {
                throw IllegalArgumentException("Комната '${req.name}' уже существует")
            }

            val room = RoomEntity(
                id = 0L,
                name = req.name,
                roomType = req.roomType,
                createdAt = java.util.Date()      // ← Date по твоей модели
            )

            // Собираем устройства без roomId — он появится в транзакции
            val devices = expand(req.devices, roomId = null)

            val (roomId, deviceIds) = roomsTxDao.insertRoomWithDevices(room, devices)
            CreatedRoomResult(roomId = roomId, deviceIds = deviceIds)
        }


    override suspend fun addDevicesToRoom(
        roomId: Long,
        devices: List<DeviceCreateRequest>
    ): List<Long> = withContext(dispatchers) {
        val entities = expand(devices, roomId = roomId) // сразу с FK
        roomsTxDao.insertDevices(entities)
    }

    override suspend fun deleteDevices(deviceIds: List<Long>) = withContext(dispatchers) {
        if (deviceIds.isNotEmpty()) roomsTxDao.deleteDevicesByIds(deviceIds)
    }

    private fun expand(
        reqs: List<DeviceCreateRequest>,
        roomId: Long? = null
    ): List<DeviceEntity> = reqs.flatMap { r ->
        val def = deviceDefaults[r.type]
        val qty = r.count.coerceAtLeast(0)
        (0 until qty).map {
            DeviceEntity(
                deviceId = 0L,
                name = r.title,                                   // ← твоё поле name
                power = r.ratedPowerW ?: def.power,               // ← Int
                voltage = r.voltage ?: def.voltage,               // ← Voltage (класс)
                demandRatio = r.demandRatio ?: def.demandRatio,   // ← Double
                createdAt = java.util.Date(),                     // ← Date()
                roomId = roomId ?: 0L,                            // ← FK — проставим в транзакции
                deviceType = r.type,                              // ← enum DeviceType
                powerFactor = r.powerFactor ?: def.powerFactor,   // ← Double
                hasMotor = def.hasMotor,
                requiresDedicatedCircuit = def.requiresDedicatedCircuit,
                requiresSocketConnection = def.requiresSocketConnection
            )
        }
    }

}

//преобразования объектов из Entity в Domain
private fun List<RoomEntity>.toDomainModelListRoom(): List<Room> {
    return map { entity ->
        Room(
            id = entity.id,
            name = entity.name,
            createdAt = entity.createdAt,
            roomType = entity.roomType
        )
    }
}

// Преобразования объектов из Domain в Entity

private fun Room.toDomainModelRoomEntity() = RoomEntity(
    id = id,
    name = name,
    createdAt = createdAt,
    roomType = roomType
)

private fun DeviceEntity.toDomainModelListDevice() = Device(
    id = deviceId,
    name = name,
    power = power,
    demandRatio = demandRatio,
    voltage = voltage,
    createdAt = createdAt,
    roomId = roomId,
    deviceType = deviceType,
    powerFactor = powerFactor
)


private fun RoomWithDevicesEntity.toDomainModelGroup(): Room {
    return Room(
        id = room.id,
        name = room.name,
        createdAt = room.createdAt,
        devices = devices.map { it.toDomainModelListDevice() },
        roomType = room.roomType
    )
}

private fun List<RoomWithDevicesEntity>.toDomainModelListRoomWithDevices(): List<RoomWithDevice> {
    return map { entity ->
        RoomWithDevice(
            room = entity.room,
            devices = entity.devices
        )
    }
}




