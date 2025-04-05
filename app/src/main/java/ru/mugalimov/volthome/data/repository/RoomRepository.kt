package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomWithLoad

interface RoomRepository {
    //поток данных с актуальным списком комнат
    fun observeRooms(): Flow<List<Room>>

    //добавить новую комнату
    suspend fun addRoom(room: Room)

    //удалить комнату
    suspend fun deleteRoom(roomId: Long)

    //получить комнату по roomId
    suspend fun getRoomById(roomId: Long) : Room?

    suspend fun getRoomsWithLoads(): Flow<List<RoomWithLoad>>

    suspend fun getDefaultRooms(): Flow<List<DefaultRoom>>
}