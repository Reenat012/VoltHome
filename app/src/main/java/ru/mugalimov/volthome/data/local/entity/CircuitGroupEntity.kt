package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import java.util.Date

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
    val groupId: Long = 0,

    @ColumnInfo(name = "group_number")
    val groupNumber: Int,        // Уникальный номер группы

    @ColumnInfo(name = "room_id", index = true)
    val roomId: Long,            // ID комнаты

    @ColumnInfo(name = "room_name")
    val roomName: String,        // Название комнаты

    @ColumnInfo(name = "group_type")
    val groupType: String,       // Тип группы (LIGHTING, SOCKET и т.д.)

    // Расчетные параметры
    @ColumnInfo(name = "nominal_current")
    val nominalCurrent: Double,  // Суммарный расчетный ток группы (А)

    @ColumnInfo(name = "circuit_breaker")
    val circuitBreaker: Int,     // Номинал автомата (А)

    @ColumnInfo(name = "cable_section")
    val cableSection: Double,    // Сечение кабеля (мм²)

    @ColumnInfo(name = "breaker_type")
    val breakerType: String,     // Тип автомата ("B", "C", "D")

    // Параметры безопасности
    @ColumnInfo(name = "rcd_required")
    val rcdRequired: Boolean,    // Требуется ли УЗО

    @ColumnInfo(name = "rcd_current")
    val rcdCurrent: Int = 30,    // Ток утечки для УЗО (мА)

    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date() // Дата создания группы
)