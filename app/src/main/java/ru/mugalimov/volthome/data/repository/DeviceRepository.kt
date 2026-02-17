package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Device

interface DeviceRepository {
    //получаем список устройств по id комнаты
    suspend fun observeDevicesByIdRoom(roomId: Long): Flow<List<Device>>

    //добавить устройство в комнату
    suspend fun addDevice(device: Device)

    //удалить комнату
    suspend fun deleteDevice(deviceId: Long)

    //получить комнату по roomId
    suspend fun getDeviceById(deviceId: Int): Device?

    // Получаем все устройства из определенной комнаты по roomId
    suspend fun getAllDevicesByRoomId(roomId: Long): List<Device>

    // Получение устройств из каталога json файла
    fun getDefaultDevices(): Flow<List<DefaultDevice>>

    // Получение всех устройств
    suspend fun getAllDevices(): List<Device>

    // ✅ Коммит 2A: batch загрузка устройств по списку id
    // Важно: метод ДЕТЕРМИНИРОВАННЫЙ — результат возвращаем в том же порядке, что ids.
    // На пустом списке — пустой результат.
    suspend fun getDevicesByIds(ids: List<Long>): List<Device>

    // Обновление инстанса устройства (имя/мощность и др.)
    suspend fun updateDevice(device: Device)
}