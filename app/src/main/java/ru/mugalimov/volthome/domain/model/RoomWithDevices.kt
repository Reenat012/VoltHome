package ru.mugalimov.volthome.domain.model

import androidx.compose.ui.tooling.preview.Devices
import androidx.room.Embedded
import androidx.room.Relation
import ru.mugalimov.volthome.data.local.entity.RoomEntity

data class RoomWithDevices(
    @Embedded val room: RoomEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "roomId"
    )
    val devices: List<Devices>
)
