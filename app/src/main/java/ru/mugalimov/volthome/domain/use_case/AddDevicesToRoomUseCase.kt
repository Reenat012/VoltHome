package ru.mugalimov.volthome.domain.use_case

import java.util.UUID
import javax.inject.Inject
import ru.mugalimov.volthome.core.validation.DeviceRequestsValidator
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest

class AddDevicesToRoomUseCase @Inject constructor(
    private val repo: RoomRepository
) {
    suspend operator fun invoke(roomId: Long, devices: List<DeviceCreateRequest>): List<Long> {
        // ✅ Доменная защита: единая точка проверки мощностей
        DeviceRequestsValidator.validateDevicesPowerOrThrow(devices)

        // ✅ Commit 1: opId корреляции (один на один вызов add-devices)
        val opId = UUID.randomUUID().toString()

        // ✅ UseCase вызывает overload с opId (а repo уже знает projectIdRecorded)
        return repo.addDevicesToRoom(
            roomId = roomId,
            devices = devices,
            opId = opId
        )
    }
}