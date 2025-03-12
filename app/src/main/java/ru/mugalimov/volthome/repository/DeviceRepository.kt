package ru.mugalimov.volthome.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.model.Device

interface DeviceRepository {
    //поток данных с актуальным списком устройств
    fun observeDevices(): Flow<List<Device>>

    //добавить новую комнату
    suspend fun addDevice(name: String, power: Int, voltage: Int, demandRatio: Double)

    //удалить комнату
    suspend fun deleteDevice(deviceId: Int)

    //получить комнату по roomId
    suspend fun getDeviceById(deviceId: Int) : Device?
}