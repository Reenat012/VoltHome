package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomWithDevice
import ru.mugalimov.volthome.domain.model.RoomWithDevicesPreview
import ru.mugalimov.volthome.domain.model.create.CreatedRoomResult
import ru.mugalimov.volthome.domain.model.create.BatchRoomCreationResult
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest
import ru.mugalimov.volthome.domain.model.PhaseMode

interface RoomRepository {

    fun observeRooms(): Flow<List<Room>>

    fun observeRoomsWithDevicesPreview(): Flow<List<RoomWithDevicesPreview>>

    suspend fun addRoom(room: Room)

    suspend fun updateRoom(room: Room)

    suspend fun deleteRoom(roomId: Long)

    suspend fun getRoomById(roomId: Long): Room?

    /**
     * ⚠️ Legacy: завязан на activeProjectId внутри репозитория.
     * Оставляем, чтобы не ломать вызывающих.
     */
    suspend fun getRoomsWithDevices(): List<RoomWithDevice>

    /**
     * ✅ NEW: явный projectId. Нужен, чтобы калькулятор не считал "в воздухе".
     */
    suspend fun getRoomsWithDevicesByProject(projectId: String): List<RoomWithDevice>

    suspend fun getDefaultRooms(): Flow<List<DefaultRoom>>

    suspend fun getAllRoom(): List<Room>

    suspend fun addRoomWithDevices(req: RoomCreateRequest): CreatedRoomResult

    suspend fun addRoomWithDevices(req: RoomCreateRequest, opId: String): CreatedRoomResult

    suspend fun addRoomsWithDevicesBatch(
        projectId: String,
        requests: List<RoomCreateRequest>,
        phaseMode: PhaseMode,
        opId: String
    ): BatchRoomCreationResult

    suspend fun addDevicesToRoom(roomId: Long, devices: List<DeviceCreateRequest>): List<Long>

    suspend fun addDevicesToRoom(
        roomId: Long,
        devices: List<DeviceCreateRequest>,
        opId: String
    ): List<Long>

    suspend fun deleteDevices(deviceIds: List<Long>): Unit?
}
