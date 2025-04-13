package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import java.util.Date

@Entity(
    tableName = "devices",
    foreignKeys = [ForeignKey(
        entity = RoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["room_id"],
        onDelete = ForeignKey.CASCADE //удаление устройств при удалении комнаты
    )]
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name") //явное указание имени столбца
    val name: String,

    @ColumnInfo(name = "power")
    val power: Int,

    @ColumnInfo(name = "voltage")
    val voltage: Voltage,

    //к-т спроса
    @ColumnInfo(name = "demand_ratio")
    val demandRatio: Double,

    @ColumnInfo(name = "created_at")
    val createdAt: Date, //временная метка создания

    //связь с комнатой через id
    @ColumnInfo(name = "room_id")
    val roomId: Long,

    @ColumnInfo(name = "device_type")
    val deviceType: DeviceType
)
