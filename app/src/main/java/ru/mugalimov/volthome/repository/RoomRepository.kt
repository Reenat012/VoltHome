package ru.mugalimov.volthome.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.entity.DeviceEntity
import ru.mugalimov.volthome.model.Device
import ru.mugalimov.volthome.model.Room

interface RoomRepository {
    //поток данных с актуальным списком комнат
    fun observeRooms(): Flow<List<Room>>

    //добавить новую комнату
    suspend fun addRoom(name: String)

    //удалить комнату
    suspend fun deleteRoom(roomId: Int)

    //получить комнату по roomId
    suspend fun getRoomById(roomId: Int) : Room?
}