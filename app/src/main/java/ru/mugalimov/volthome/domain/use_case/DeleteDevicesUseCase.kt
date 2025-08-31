package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.RoomRepository

class DeleteDevicesUseCase @Inject constructor(
    private val repo: RoomRepository
) {
    suspend operator fun invoke(deviceIds: List<Long>) = repo.deleteDevices(deviceIds)
}