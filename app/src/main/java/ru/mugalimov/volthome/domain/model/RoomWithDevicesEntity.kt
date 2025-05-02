package ru.mugalimov.volthome.domain.model

import androidx.room.Embedded
import androidx.room.Relation
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity

data class RoomWithDevicesEntity(
    @Embedded val room: RoomEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "room_id"
    )
    val devices: List<DeviceEntity>
)
