package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "group_device_join",
    primaryKeys = ["group_id", "device_id"],
    foreignKeys = [
        ForeignKey(
            entity = CircuitGroupEntity::class,
            parentColumns = ["group_id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["device_id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]

)
data class GroupDeviceJoin(
    @ColumnInfo(name = "group_id")
    val groupId: Long,
    @ColumnInfo(name = "device_id")
    val deviceId: Long
)