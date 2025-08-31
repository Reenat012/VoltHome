package ru.mugalimov.volthome.domain.model.create

import ru.mugalimov.volthome.domain.model.RoomType

data class RoomCreateRequest(
    val name: String,
    val roomType: RoomType,
    val devices: List<DeviceCreateRequest>
)
