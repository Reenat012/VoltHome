package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "loads",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["room_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_loads_room_id", value = ["room_id"]),
        Index(name = "idx_loads_project_id", value = ["project_id"])
    ]
)
data class LoadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "current")
    val currentRoom: Double = 0.0,

    @ColumnInfo(name = "sum_power")
    val powerRoom: Int = 0,

    @ColumnInfo(name = "count_devices")
    val countDevices: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Date,

    @ColumnInfo(name = "room_id")
    val roomId: Long,

    // 🔹 Привязка к проекту
    @ColumnInfo(name = "project_id")
    val projectId: String? = null
)