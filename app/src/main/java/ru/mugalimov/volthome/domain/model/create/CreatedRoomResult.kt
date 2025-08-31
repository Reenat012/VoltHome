package ru.mugalimov.volthome.domain.model.create

data class CreatedRoomResult(
    val roomId: Long,
    val deviceIds: List<Long>
)
