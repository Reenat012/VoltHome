package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.mugalimov.volthome.domain.model.RoomType
import java.util.Date

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name") //явное указание имени столбца
    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Date,

    @ColumnInfo(name = "room_type")
    val roomType: RoomType
)