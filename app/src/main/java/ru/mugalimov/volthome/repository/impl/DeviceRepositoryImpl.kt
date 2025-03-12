package ru.mugalimov.volthome.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.dao.DeviceDao
import ru.mugalimov.volthome.dao.RoomDao
import ru.mugalimov.volthome.entity.DeviceEntity
import ru.mugalimov.volthome.entity.RoomEntity
import ru.mugalimov.volthome.error.DeviceNotFoundException
import ru.mugalimov.volthome.error.RoomAlreadyExistsException
import ru.mugalimov.volthome.error.RoomNotFoundException
import ru.mugalimov.volthome.model.Device
import ru.mugalimov.volthome.model.Room
import ru.mugalimov.volthome.module.IoDispatcher
import ru.mugalimov.volthome.repository.DeviceRepository
import ru.mugalimov.volthome.repository.RoomRepository
import java.util.Date
import javax.inject.Inject

class DeviceRepositoryImpl @Inject constructor(
    private val deviceDao: DeviceDao,
    //свойство dispatchers, которое хранит диспетчер для запуска корутин
    //в фоновых потоках, подходящих для IO-задач
    @IoDispatcher private val dispatchers: CoroutineDispatcher
) : DeviceRepository {

    //получение списка комнат через DAO
    //используется для получения изменения данных
    override fun observeDevices(): Flow<List<Device>> {
        return deviceDao.observeAllDevice()
            //преобразуем список DeviceEntity в список Device
            .map { entities -> entities.toDomainModel() }
            .flowOn(dispatchers)
    }


    override suspend fun addDevice(name: String, power: Int, voltage: Int, demandRatio: Double) {
        //запускаем в фоновом потоке, используя корутину
        withContext(dispatchers) {

            //добавляем устройство в БД
            deviceDao.addDevices(
                DeviceEntity(
                    name = name,
                    power = power,
                    voltage = voltage,
                    demandRatio = demandRatio,
                    createdAt = Date()
                )
            )
        }
    }

    override suspend fun deleteDevice(deviceId: Int) {
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
            entity?.toDomain()
        } catch (e: Exception) {
            throw DeviceNotFoundException()
        }
    }
}

//преобразования объектов из Entity в Domain
private fun List<DeviceEntity>.toDomainModel(): List<Device> {
    return map { entity ->
        Device(
            id = entity.id,
            name = entity.name,
            power = entity.power,
            voltage = entity.voltage,
            demandRatio = entity.demandRatio,
            createdAt = entity.createdAt
        )
    }
}


private fun DeviceEntity.toDomain() = Device(
    id = id,
    name = name,
    power = power,
    demandRatio = demandRatio,
    voltage = voltage,
    createdAt = createdAt
)
