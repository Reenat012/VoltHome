package ru.mugalimov.volthome.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "loads",
    foreignKeys = [ForeignKey(
        entity = RoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["room_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class LoadEntity(
    @PrimaryKey
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "current")
    val current: Double,

    @ColumnInfo(name = "sum_power")
    val sumPower: Int,

    @ColumnInfo(name = "count_devices")
    val countDevices: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Date,

    @ColumnInfo(name = "room_id")
    val roomId: Int
)