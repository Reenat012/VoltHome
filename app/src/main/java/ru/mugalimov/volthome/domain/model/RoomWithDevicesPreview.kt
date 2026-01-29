package ru.mugalimov.volthome.domain.model

data class RoomWithDevicesPreview(
    val roomId: Long,
    val name: String,
    val roomType: RoomType,
    val devicesCount: Int,
    val devicesPreview: List<DevicePreview>
)