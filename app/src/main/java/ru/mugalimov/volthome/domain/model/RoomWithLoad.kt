package ru.mugalimov.volthome.domain.model

import androidx.room.Embedded
import androidx.room.Relation
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity

data class RoomWithLoad(
    @Embedded val room: RoomEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "room_id"
    )
    val loads: List<LoadEntity> // Список нагрузок для комнаты
)
