package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest

class AddDevicesToRoomUseCase @Inject constructor(
    private val repo: RoomRepository
) {
    suspend operator fun invoke(roomId: Long, devices: List<DeviceCreateRequest>): List<Long> =
        repo.addDevicesToRoom(roomId, devices)
}