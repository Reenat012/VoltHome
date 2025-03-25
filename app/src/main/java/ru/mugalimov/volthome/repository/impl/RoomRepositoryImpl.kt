package ru.mugalimov.volthome.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.dao.DeviceDao
import ru.mugalimov.volthome.dao.RoomDao
import ru.mugalimov.volthome.entity.DeviceEntity
import ru.mugalimov.volthome.entity.RoomEntity
import ru.mugalimov.volthome.error.RoomAlreadyExistsException
import ru.mugalimov.volthome.error.RoomNotFoundException
import ru.mugalimov.volthome.model.Device
import ru.mugalimov.volthome.model.Room
import ru.mugalimov.volthome.module.IoDispatcher
import ru.mugalimov.volthome.repository.RoomRepository
import java.util.Date
import javax.inject.Inject

class RoomRepositoryImpl @Inject constructor(
    private val roomDao: RoomDao,
    private val deviceDao: DeviceDao,
    //свойство dispatchers, которое хранит диспетчер для запуска корутин
    //в фоновых потоках, подходящих для IO-задач
    @IoDispatcher private val dispatchers: CoroutineDispatcher
) : RoomRepository {

    //получение списка комнат через DAO
    //используется для получения изменения данных
    override fun observeRooms(): Flow<List<Room>> {
        return roomDao.observeAllRooms()
            //преобразуем список RoomEntity в список Room
            .map { entities -> entities.toDomainModelListRoom() }
            .flowOn(dispatchers)
    }


    override suspend fun addRoom(name: String) {
        //запускаем в фоновом потоке, используя корутину
        withContext(dispatchers) {
            //проверка на существование комнаты по имени
            if (roomDao.existsByName(name)) {
                throw RoomAlreadyExistsException("Комната с именем '$name' уже существует")
            }

            //добавляем комнату в БД
            roomDao.addRoom(RoomEntity(name = name, createdAt = Date()))
        }
    }

    override suspend fun deleteRoom(roomId: Int) {
        //запускаем в фоновом потоке, используя корутину
        withContext(dispatchers) {
            //записываем в переменную число удаленных строк
            val rowsDeleted = roomDao.deleteRoomById(roomId)
            //проверяем удалилось что-нибудь или нет
            if (rowsDeleted == 0) {
                throw RoomNotFoundException("Комната с ID $roomId не найдена")
            }
        }
    }

    override suspend fun getRoomById(roomId: Int): Room? = withContext(dispatchers) {
        return@withContext try {
            //ищем комнату в БД
            val entity = roomDao.getRoomById(roomId)

            //преобразовываем из Entity в модель удобную для чтения
            entity?.toDomainModelRoom()
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


private fun RoomEntity.toDomainModelRoom() = Room(
    id = id,
    name = name,
    createdAt = createdAt
)

private fun List<DeviceEntity>.toDomainModelListDevice(): List<Device> {
    return map { entity ->
        Device(
            id = entity.id,
            name = entity.name,
            power = entity.power,
            voltage = entity.voltage,
            demandRatio = entity.demandRatio,
            createdAt = entity.createdAt,
            roomId = entity.roomId
        )
    }
}


private fun DeviceEntity.toDomainModelDevice() = Device(
    id = id,
    name = name,
    power = power,
    demandRatio = demandRatio,
    voltage = voltage,
    createdAt = createdAt,
    roomId = roomId
)


