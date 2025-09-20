package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.mugalimov.volthome.domain.model.RoomType
import java.util.Date

@Entity(
    tableName = "rooms",
    indices = [
        // имя комнаты может повторяться в разных проектах
        Index(name = "uq_rooms_name_project", value = ["name", "project_id"], unique = true),
        Index(name = "idx_rooms_project_id", value = ["project_id"])
    ]
)
data class RoomEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Date,

    @ColumnInfo(name = "room_type")
    val roomType: RoomType,

    // 🔹 Привязка к проекту
    @ColumnInfo(name = "project_id")
    val projectId: String? = null
)