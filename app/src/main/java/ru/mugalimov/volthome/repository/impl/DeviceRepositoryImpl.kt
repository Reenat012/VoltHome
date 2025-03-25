package ru.mugalimov.volthome.repository.impl

import android.content.ContentValues.TAG
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.dao.DeviceDao
import ru.mugalimov.volthome.dao.RoomDao
import ru.mugalimov.volthome.entity.DeviceEntity
import ru.mugalimov.volthome.error.DeviceNotFoundException
import ru.mugalimov.volthome.model.Device
import ru.mugalimov.volthome.module.IoDispatcher
import ru.mugalimov.volthome.repository.DeviceRepository
import java.util.Date
import javax.inject.Inject

class DeviceRepositoryImpl @Inject constructor(
    private val deviceDao: DeviceDao,
    private val roomDao: RoomDao,
    //свойство dispatchers, которое хранит диспетчер для запуска корутин
    //в фоновых потоках, подходящих для IO-задач
    @IoDispatcher private val dispatchers: CoroutineDispatcher
) : DeviceRepository {

    //получение списка комнат через DAO
    //используется для получения изменения данных
    override suspend fun observeDevicesByIdRoom(roomId: Long): Flow<List<Device>> {
        return deviceDao.observeDevicesByIdRoom(roomId)
            //преобразуем список DeviceEntity в список Device
            .map { entities -> entities.toDomainModelListDevice() }
            .flowOn(dispatchers)
    }

    override suspend fun addDevice(name: String, power: Int, voltage: Int, demandRatio: Double, roomId: Long) {
        Log.d(TAG, "Заходим в репо")
        //запускаем в фоновом потоке, используя корутину
        try {
            withContext(dispatchers) {
                val isRoomExist = roomDao.getRoomById(roomId)
                if (isRoomExist == null) {
                    throw IllegalArgumentException("Комната с ID $roomId не найдена")
                }

                Log.d(TAG, "Заходим в корутину")
                deviceDao.addDevice(
                    DeviceEntity(
                        name = name,
                        power = power,
                        voltage = voltage,
                        demandRatio = demandRatio,
                        roomId = roomId,
                        createdAt = Date()
                    )
                )
                Log.d(TAG, "Успех")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при добавлении устройства: ${e.message}", e)
            throw e // Перебрасываем исключение дальше
        }
    }

    override suspend fun deleteDevice(deviceId: Long) {
        //запускаем в фоновом потоке, используя корутину
        withContext(dispatchers) {
            //записываем в переменную число удаленных строк
            val rowsDeleted = deviceDao.deleteDeviceById(deviceId)
            //проверяем удалилось что-нибудь или нет
            if (rowsDeleted == 0) {
                throw DeviceNotFoundException("Устройство с ID $deviceId не найдено")
            }
        }
    }

    override suspend fun getDeviceById(deviceId: Int): Device? = withContext(dispatchers) {
        return@withContext try {
            //ищем устройство в БД
            val entity = deviceDao.getDeviceById(deviceId)

            //преобразовываем из Entity в модель удобную для чтения
            entity?.toDomainModelDevice()
        } catch (e: Exception) {
            throw DeviceNotFoundException()
        }
    }
}

//преобразования объектов из Entity в Domain
private fun List<DeviceEntity>.toDomainModelListDevice(): List<Device> {
    return map { entity ->
        Device(
            id = entity.id,
            name = entity.name,
            power = entity.power,
            voltage = entity.voltage,
            demandRatio = entity.demandRatio,
            roomId = entity.roomId,
            createdAt = entity.createdAt
        )
    }
}


private fun DeviceEntity.toDomainModelDevice() = Device(
    id = id,
    name = name,
    power = power,
    demandRatio = demandRatio,
    voltage = voltage,
    roomId = roomId,
    createdAt = createdAt
)
