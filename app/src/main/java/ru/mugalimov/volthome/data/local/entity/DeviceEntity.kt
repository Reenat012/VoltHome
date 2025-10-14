package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import java.util.Date

@Entity(
    tableName = "devices",
    indices = [
        Index(name = "idx_devices_room_id", value = ["room_id"]),
        Index(name = "idx_devices_project_id", value = ["project_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["room_id"],
            onDelete = ForeignKey.SET_NULL   // <= база уже так, приводим Entity к ней
        )
    ]
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "device_id")
    val deviceId: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "power")
    val power: Int,

    @ColumnInfo(name = "voltage")
    val voltage: Voltage,

    @ColumnInfo(name = "demand_ratio")
    val demandRatio: Double,

    @ColumnInfo(name = "created_at")
    val createdAt: Date,

    // 🔹 теперь nullable, чтобы совпасть с БД (Found: notNull=false)
    @ColumnInfo(name = "room_id")
    val roomId: Long?,

    @ColumnInfo(name = "device_type")
    val deviceType: DeviceType,

    @ColumnInfo(name = "power_factor")
    val powerFactor: Double,

    // 🔹 проставляем defaultValue, чтобы совпасть с БД (Found: 0/1)
    @ColumnInfo(name = "has_motor", defaultValue = "0")
    val hasMotor: Boolean = false,

    @ColumnInfo(name = "requires_dedicated", defaultValue = "0")
    val requiresDedicatedCircuit: Boolean = false,

    @ColumnInfo(name = "requires_socket", defaultValue = "1")
    val requiresSocketConnection: Boolean = true,

    // 🔹 привязка к проекту (nullable)
    @ColumnInfo(name = "project_id")
    val projectId: String? = null
)