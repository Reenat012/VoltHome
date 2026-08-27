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
        Index(name = "idx_devices_project_id", value = ["project_id"]),
        // ⬇️ НОВОЕ: индекс по name, чтобы совпасть с миграцией MIGRATION_21_22
        Index(name = "idx_devices_name", value = ["name"]),
        Index(
            name = "idx_devices_project_room_created_id",
            value = ["project_id", "room_id", "created_at", "device_id"]
        )
    ],
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["room_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
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

    @ColumnInfo(name = "room_id")
    val roomId: Long?,

    @ColumnInfo(name = "device_type")
    val deviceType: DeviceType,

    @ColumnInfo(name = "power_factor")
    val powerFactor: Double,

    @ColumnInfo(name = "has_motor", defaultValue = "0")
    val hasMotor: Boolean = false,

    @ColumnInfo(name = "requires_dedicated", defaultValue = "0")
    val requiresDedicatedCircuit: Boolean = false,

    @ColumnInfo(name = "requires_socket", defaultValue = "1")
    val requiresSocketConnection: Boolean = true,

    @ColumnInfo(name = "project_id")
    val projectId: String
)
