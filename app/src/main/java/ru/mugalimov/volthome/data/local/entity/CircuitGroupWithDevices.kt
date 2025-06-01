package ru.mugalimov.volthome.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

// Новая модель для связи
data class CircuitGroupWithDevices(
    @Embedded val group: CircuitGroupEntity,
    @Relation(
        parentColumn = "group_id",
        entityColumn = "device_id",
        associateBy = Junction(GroupDeviceJoin::class)
    )
    val devices: List<DeviceEntity>
)
