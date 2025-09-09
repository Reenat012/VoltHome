package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.core.validation.DeviceRequestsValidator
import ru.mugalimov.volthome.core.validation.PowerValidator
import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest
import ru.mugalimov.volthome.domain.model.create.CreatedRoomResult

class CreateRoomWithDevicesUseCase @Inject constructor(
    private val repo: RoomRepository
) {
    suspend operator fun invoke(req: RoomCreateRequest): CreatedRoomResult {
        DeviceRequestsValidator.validateDevicesPowerOrThrow(req.devices)
        return repo.addRoomWithDevices(req)
    }
}