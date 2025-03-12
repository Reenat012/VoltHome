package ru.mugalimov.volthome.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name") //явное указание имени столбца
    val name: String,

    @ColumnInfo(name = "power")
    val power: Int,

    @ColumnInfo(name = "voltage")
    val voltage: Int,

    //к-т спроса
    @ColumnInfo(name = "demand_ratio")
    val demandRatio: Double,

    @ColumnInfo(name = "created_at")
    val createdAt: Date //временная метка создания
)
