package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.domain.model.Device

interface DeviceRepository {
    //получаем список устройств по id комнаты
    suspend fun observeDevicesByIdRoom(roomId: Long) : Flow<List<Device>>

    //добавить устройство в комнату
    suspend fun addDevice(name: String, power: Int, voltage: Int, demandRatio: Double, roomId: Long)

    //удалить комнату
    suspend fun deleteDevice(deviceId: Long)

    //получить комнату по roomId
    suspend fun getDeviceById(deviceId: Int) : Device?

    // Получаем все устройства из определенной комнаты по roomId
    suspend fun getAllDevicesByRoomId(roomId: Long): List<Device>
}