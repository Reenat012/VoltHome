package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.RoomRepository

class DeleteRoomUseCase @Inject constructor(
    private val repo: RoomRepository
) {
    suspend operator fun invoke(roomId: Long) = repo.deleteRoom(roomId)
}