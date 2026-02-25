package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomWithDevice
import ru.mugalimov.volthome.domain.model.RoomWithLoad
import ru.mugalimov.volthome.domain.model.RoomWithDevicesPreview
import ru.mugalimov.volthome.domain.model.create.CreatedRoomResult
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest

interface RoomRepository {

    // поток данных с актуальным списком комнат
    fun observeRooms(): Flow<List<Room>>

    /**
     * Единый реактивный источник данных для RoomsList:
     * Room + devicesCount + devicesPreview (ограниченный список)
     *
     * ВАЖНО: должен быть Flow (не snapshot), привязан к activeProjectId реактивно.
     */
    fun observeRoomsWithDevicesPreview(): Flow<List<RoomWithDevicesPreview>>

    // добавить новую комнату
    suspend fun addRoom(room: Room)

    // обновить комнату
    suspend fun updateRoom(room: Room)

    // удалить комнату
    suspend fun deleteRoom(roomId: Long)

    // получить комнату по roomId
    suspend fun getRoomById(roomId: Long): Room?

    suspend fun getRoomsWithLoads(): Flow<List<RoomWithLoad>>

    suspend fun getRoomsWithDevices(): List<RoomWithDevice>

    suspend fun getDefaultRooms(): Flow<List<DefaultRoom>>

    suspend fun getAllRoom(): List<Room>

    /**
     * ✅ Старый контракт оставляем (чтобы не сломать всех вызывающих).
     * Внутри реализации можно генерировать opId автоматически.
     */
    suspend fun addRoomWithDevices(req: RoomCreateRequest): CreatedRoomResult

    /**
     * ✅ Commit 2: новый overload для корреляции логов/диагностики (room-create).
     * UseCase вызывает именно этот метод.
     */
    suspend fun addRoomWithDevices(req: RoomCreateRequest, opId: String): CreatedRoomResult

    /**
     * ✅ Старый контракт оставляем (чтобы не сломать всех вызывающих).
     * Внутри реализации можно генерировать opId автоматически.
     */
    suspend fun addDevicesToRoom(roomId: Long, devices: List<DeviceCreateRequest>): List<Long>

    /**
     * ✅ Commit 1: новый overload для корреляции логов/диагностики.
     * UseCase вызывает именно этот метод.
     */
    suspend fun addDevicesToRoom(
        roomId: Long,
        devices: List<DeviceCreateRequest>,
        opId: String
    ): List<Long>

    suspend fun deleteDevices(deviceIds: List<Long>) : Unit?
}