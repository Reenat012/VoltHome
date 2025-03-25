package ru.mugalimov.volthome.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.entity.DeviceEntity
import ru.mugalimov.volthome.model.Device

interface DeviceRepository {
    //получаем список устройств по id комнаты
    suspend fun observeDevicesByIdRoom(roomId: Int) : Flow<List<Device>>

    //добавить устройство в комнату
    suspend fun addDevice(name: String, power: Int, voltage: Int, demandRatio: Double, roomId: Int)

    //удалить комнату
    suspend fun deleteDevice(deviceId: Int)

    //получить комнату по roomId
    suspend fun getDeviceById(deviceId: Int) : Device?
}