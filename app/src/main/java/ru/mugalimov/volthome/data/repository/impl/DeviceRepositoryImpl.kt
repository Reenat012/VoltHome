package ru.mugalimov.volthome.data.repository.impl

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.core.error.DeviceNotFoundException
import ru.mugalimov.volthome.core.error.RoomNotFoundException
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.ui.components.JsonParser
import java.util.Date
import javax.inject.Inject

class DeviceRepositoryImpl @Inject constructor(
    private val deviceDao: DeviceDao,
    private val roomDao: RoomDao,
    private val explicationRepository: ExplicationRepository,
    //свойство dispatchers, которое хранит диспетчер для запуска корутин
    //в фоновых потоках, подходящих для IO-задач
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : DeviceRepository {

    //получение списка комнат через DAO
    //используется для получения изменения данных
    override suspend fun observeDevicesByIdRoom(roomId: Long): Flow<List<Device>> {
        return deviceDao.observeDevicesByIdRoom(roomId)
            //преобразуем список DeviceEntity в список Device
            .map { entities -> entities.toDomainModelListDevice() }
            .flowOn(dispatchers)
    }

    override suspend fun addDevice(
        device: Device
    ) {
        Log.d(TAG, "Заходим в репо")
        //запускаем в фоновом потоке, используя корутину
        try {
            withContext(dispatchers) {
                val isRoomExist = roomDao.getRoomById(device.roomId)
                if (isRoomExist == null) {
                    throw IllegalArgumentException("Комната с ID ${device.roomId} не найдена")
                }

                Log.d(TAG, "Заходим в корутину")
                deviceDao.addDevice(
                    DeviceEntity(
                        name = device.name,
                        power = device.power,
                        voltage = device.voltage,
                        demandRatio = device.demandRatio,
                        roomId = device.roomId,
                        createdAt = Date(),
                        deviceType = device.deviceType
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

            // Обработка удаления связанных с устройством групп
            explicationRepository.handleDeviceDeletion(deviceId)
        }
    }

    override suspend fun getDeviceById(deviceId: Int): Device? =
        withContext(dispatchers) {
            return@withContext try {
                //ищем устройство в БД
                val entity = deviceDao.getDeviceById(deviceId)

                //преобразовываем из Entity в модель удобную для чтения
                entity?.toDomainModelListDevice()
            } catch (e: Exception) {
                throw DeviceNotFoundException()
            }
        }

    override suspend fun getAllDevicesByRoomId(roomId: Long): List<Device> =
        withContext(dispatchers) {
            return@withContext try {
                val listDeviceEntity = deviceDao.getAllDevicesByRoomId(roomId)

                listDeviceEntity.toDomainModelListDevice()

            } catch (e: Exception) {
                throw RoomNotFoundException()
            }
        }

    override suspend fun getDefaultDevices(): Flow<List<DefaultDevice>> {
        return try {
            JsonParser.parseDevices(context)
        } catch (e: Exception) {
            emptyFlow()
        }
    }

    override suspend fun getAllDevices(): List<Device> =
        withContext(dispatchers) {
            return@withContext try {
                deviceDao.getAllDevices().toDomainModelListDevice()
            } catch (e: Exception) {
                throw DeviceNotFoundException()
            }
        }
}

//преобразования объектов из Entity в Domain
private fun List<DeviceEntity>.toDomainModelListDevice(): List<Device> {
    return map { entity ->
        Device(
            id = entity.deviceId,
            name = entity.name,
            power = entity.power,
            voltage = entity.voltage,
            demandRatio = entity.demandRatio,
            roomId = entity.roomId,
            createdAt = entity.createdAt,
            deviceType = entity.deviceType
        )
    }
}


private fun DeviceEntity.toDomainModelListDevice() = Device(
    id = deviceId,
    name = name,
    power = power,
    demandRatio = demandRatio,
    voltage = voltage,
    roomId = roomId,
    createdAt = createdAt,
    deviceType = deviceType
)
