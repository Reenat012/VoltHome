package ru.mugalimov.volthome.model

import androidx.room.Embedded
import androidx.room.Relation
import ru.mugalimov.volthome.entity.LoadEntity
import ru.mugalimov.volthome.entity.RoomEntity

data class RoomWithLoad(
    @Embedded val room: RoomEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "room_id"
    )
    val loads: List<LoadEntity> // Список нагрузок для комнаты
)
