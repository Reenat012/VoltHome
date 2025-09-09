package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest

class AddDevicesToRoomUseCase @Inject constructor(
    private val repo: RoomRepository
) {
    suspend operator fun invoke(roomId: Long, devices: List<DeviceCreateRequest>): List<Long> {
        // Validate each device rated power
        devices.forEach { req ->
            val err =
                ru.mugalimov.volthome.core.validation.PowerValidator.errorMessage(req.ratedPowerW?.toInt()
                    ?: 1)
            require(err == null) { "«${req.title}»: $err" }
        }
        return repo.addDevicesToRoom(roomId, devices)
    }
}