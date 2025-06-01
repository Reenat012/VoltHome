package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType

@Entity(
    tableName = "groups",
    foreignKeys = [ForeignKey(
        entity = RoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["room_id"],
        onDelete = ForeignKey.CASCADE
    )])
data class CircuitGroupEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "group_id")
    val groupId: Long,

    @ColumnInfo(name = "room_id", index = true)
    val roomId: Long,

    @ColumnInfo(name = "room_name")
    val roomName: String,

    @ColumnInfo(name = "group_type")
    val groupType: DeviceType,

    @ColumnInfo(name = "devices")
    val devices: List<Device>,

    @ColumnInfo(name = "nominal_current")
    val nominalCurrent: Double,

    @ColumnInfo(name = "circuit_breaker")
    val circuitBreaker: Int,

    @ColumnInfo(name = "cable_selection")
    val cableSection: Double,

    @ColumnInfo(name = "group_number")
    val groupNumber: Int
)