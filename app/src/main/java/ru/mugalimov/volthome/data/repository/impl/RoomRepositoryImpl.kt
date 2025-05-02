package ru.mugalimov.volthome.data.repository.impl

import android.content.Context
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
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.Load
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomWithLoad
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.RoomWithDevice
import ru.mugalimov.volthome.domain.model.RoomWithDevicesEntity
import ru.mugalimov.volthome.ui.components.JsonParser
import java.util.Date
import javax.inject.Inject

class RoomRepositoryImpl @Inject constructor(
    private val roomDao: RoomDao,
    private val deviceDao: DeviceDao,
    private val loadDao: LoadDao,
    private val explicationRepository: ExplicationRepository,
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
            roomDao.addRoom(RoomEntity(name = room.name, createdAt = Date())).let { roomId ->
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
            return@withContext roomDao.observeAllRoomsWithDevices().toDomainModelListRoomWithDevices()
        }

    override suspend fun getDefaultRooms(): Flow<List<DefaultRoom>> {
        return try {
            JsonParser.parseRooms(context)
        } catch (e: Exception) {
            emptyFlow()
        }
    }

    override suspend fun getAllRoom(): List<Room> =
        withContext(dispatchers) {
            return@withContext try {
                roomDao.getAllRooms().toDomainModelListRoom()
            } catch (e: Exception) {
                throw RoomNotFoundException()
            }
        }
}

//преобразования объектов из Entity в Domain
private fun List<RoomEntity>.toDomainModelListRoom(): List<Room> {
    return map { entity ->
        Room(
            id = entity.id,
            name = entity.name,
            createdAt = entity.createdAt
        )
    }
}

//преобразования объектов из Domain в Entity
private fun List<Room>.toDomainModelListRoomEntity(): List<RoomEntity> {
    return map { entity ->
        RoomEntity(
            id = entity.id,
            name = entity.name,
            createdAt = entity.createdAt
        )
    }
}


private fun RoomEntity.toDomainModelRoom() = Room(
    id = id,
    name = name,
    createdAt = createdAt
)

private fun Room.toDomainModelRoomEntity() = RoomEntity(
    id = id,
    name = name,
    createdAt = createdAt
)

private fun List<DeviceEntity>.toDomainModelListDevice(): List<Device> {
    return map { entity ->
        Device(
            id = entity.deviceId,
            name = entity.name,
            power = entity.power,
            voltage = entity.voltage,
            demandRatio = entity.demandRatio,
            createdAt = entity.createdAt,
            roomId = entity.roomId,
            deviceType = entity.deviceType
        )
    }
}

private fun List<Device>.toDomainModelListDeviceEntity(): List<DeviceEntity> {
    return map { entity ->
        DeviceEntity(
            deviceId = entity.id,
            name = entity.name,
            power = entity.power,
            voltage = entity.voltage,
            demandRatio = entity.demandRatio,
            createdAt = entity.createdAt,
            roomId = entity.roomId,
            deviceType = entity.deviceType
        )
    }
}


private fun Device.toDomainModelListDevice() = DeviceEntity(
    deviceId = id,
    name = name,
    power = power,
    demandRatio = demandRatio,
    voltage = voltage,
    createdAt = createdAt,
    roomId = roomId,
    deviceType = deviceType
)

private fun DeviceEntity.toDomainModelListDevice() = Device(
    id = deviceId,
    name = name,
    power = power,
    demandRatio = demandRatio,
    voltage = voltage,
    createdAt = createdAt,
    roomId = roomId,
    deviceType = deviceType
)

fun toLoad(entity: LoadEntity): Load {
    return Load(
        id = entity.id,
        name = entity.name,
        current = entity.currentRoom,
        sumPower = entity.powerRoom,
        countDevices = entity.countDevices,
        createdAt = entity.createdAt,
        roomId = entity.roomId
    )
}

private fun RoomWithDevicesEntity.toDomainModelGroup(): Room {
    return Room(
        id = room.id,
        name = room.name,
        createdAt = room.createdAt,
        devices = devices.map { it.toDomainModelListDevice() }
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




