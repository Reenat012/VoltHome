package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "total_load")
data class TotalLoadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,

    @ColumnInfo(name = "count_rooms")
    val countRooms: Int,

    @ColumnInfo(name = "power")
    val power: Int,

    @ColumnInfo(name = "current")
    val current: Double,

    @ColumnInfo(name = "created_at")
    val createdAt: Date
)
