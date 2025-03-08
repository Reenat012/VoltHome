package ru.mugalimov.volthome.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.dao.RoomDao
import ru.mugalimov.volthome.entity.RoomEntity
import ru.mugalimov.volthome.error.RoomAlreadyExistsException
import ru.mugalimov.volthome.error.RoomNotFoundException
import ru.mugalimov.volthome.model.Room
import ru.mugalimov.volthome.module.IoDispatcher
import ru.mugalimov.volthome.repository.RoomRepository
import java.util.Date
import javax.inject.Inject

class RoomRepositoryImpl @Inject constructor(
    private val roomDao: RoomDao,
    //свойство dispatchers, которое хранит диспетчер для запуска корутин
    //в фоновых потоках, подходящих для IO-задач
    @IoDispatcher private val dispatchers: CoroutineDispatcher
) : RoomRepository {

    //получение списка комнат через DAO
    //используется для получения изменения данных
    override fun observeRooms(): Flow<List<Room>> {
        return roomDao.observeAllRooms()
            //преобразуем список RoomEntity в список Room
            .map { entities -> entities.toDomainModel() }
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

    override suspend fun getRoomById(roomId: Int) : Room? = withContext(dispatchers) {
            return@withContext try {
                //ищем комнату в БД
                val entity = roomDao.getRoomById(roomId)

                //преобразовываем из Entity в модель удобную для чтения
                entity?.toDomain()
            } catch (e: Exception) {
                throw RoomNotFoundException()
            }
        }
    }

//преобразования объектов из Entity в Domain
    private fun List<RoomEntity>.toDomainModel(): List<Room> {
        return map { entity -> Room(
            id = entity.id,
            name = entity.name,
            createdAt = entity.createdAt
        ) }
    }


    private fun RoomEntity.toDomain() = Room(
        id = id,
        name = name,
        createdAt = createdAt
    )

