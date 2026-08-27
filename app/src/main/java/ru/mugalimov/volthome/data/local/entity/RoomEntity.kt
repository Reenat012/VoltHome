package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.mugalimov.volthome.domain.model.RoomType
import java.util.Date

@Entity(
    tableName = "rooms",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        // Имя комнаты может повторяться в разных проектах,
        // поэтому уникальность по (name, project_id)
        Index(
            name = "uq_rooms_name_project",
            value = ["name", "project_id"],
            unique = true
        ),
        Index(
            name = "idx_rooms_project_id",
            value = ["project_id"]
        ),
        Index(
            name = "idx_rooms_project_created_id",
            value = ["project_id", "created_at", "id"]
        )
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

    // Привязка к проекту
    @ColumnInfo(name = "project_id")
    val projectId: String
)
