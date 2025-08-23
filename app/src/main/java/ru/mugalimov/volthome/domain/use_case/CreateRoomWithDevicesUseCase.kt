package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest
import ru.mugalimov.volthome.domain.model.create.CreatedRoomResult

class CreateRoomWithDevicesUseCase @Inject constructor(
    private val repo: RoomRepository
) {
    suspend operator fun invoke(req: RoomCreateRequest): CreatedRoomResult =
        repo.addRoomWithDevices(req)
}