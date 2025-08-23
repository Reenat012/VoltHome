package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomWithDevice
import ru.mugalimov.volthome.domain.model.RoomWithDevicesEntity
import ru.mugalimov.volthome.domain.model.RoomWithLoad
import ru.mugalimov.volthome.domain.model.create.CreatedRoomResult
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest

interface RoomRepository {
    //поток данных с актуальным списком комнат
    fun observeRooms(): Flow<List<Room>>

    //добавить новую комнату
    suspend fun addRoom(room: Room)

    // Обновить комнату после добавления списка устройств
    suspend fun updateRoom(room: Room)

    //удалить комнату
    suspend fun deleteRoom(roomId: Long)

    //получить комнату по roomId
    suspend fun getRoomById(roomId: Long): Room?

    suspend fun getRoomsWithLoads(): Flow<List<RoomWithLoad>>

    suspend fun getRoomsWithDevices(): List<RoomWithDevice>

    suspend fun getDefaultRooms(): Flow<List<DefaultRoom>>

    suspend fun getAllRoom(): List<Room>

    suspend fun addRoomWithDevices(req: RoomCreateRequest): CreatedRoomResult
    suspend fun addDevicesToRoom(roomId: Long, devices: List<DeviceCreateRequest>): List<Long>
    suspend fun deleteDevices(deviceIds: List<Long>)
}