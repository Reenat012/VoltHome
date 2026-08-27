package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.mugalimov.volthome.domain.model.Phase
import java.util.Date

@Entity(
    tableName = "groups",
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
    ],
    indices = [
        Index(name = "idx_groups_room_id", value = ["room_id"]),
        Index(name = "idx_groups_project_id", value = ["project_id"]),
        Index(name = "idx_groups_project_room_id", value = ["project_id", "room_id"])
    ]
)
data class CircuitGroupEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "group_id")
    val groupId: Long = 0,

    @ColumnInfo(name = "group_number")
    val groupNumber: Int,

    @ColumnInfo(name = "room_id")
    val roomId: Long,

    @ColumnInfo(name = "room_name")
    val roomName: String,

    @ColumnInfo(name = "group_type")
    val groupType: String,

    @ColumnInfo(name = "nominal_current")
    val nominalCurrent: Double,

    @ColumnInfo(name = "circuit_breaker")
    val circuitBreaker: Int,

    @ColumnInfo(name = "cable_section")
    val cableSection: Double,

    @ColumnInfo(name = "breaker_type")
    val breakerType: String,

    @ColumnInfo(name = "rcd_required")
    val rcdRequired: Boolean,

    @ColumnInfo(name = "rcd_current")
    val rcdCurrent: Int = 30,

    @ColumnInfo(name = "rcd_reason_codes")
    val rcdReasonCodes: String = "",

    @ColumnInfo(name = "rcd_nominal_current")
    val rcdNominalCurrent: Int? = null,

    @ColumnInfo(name = "rcd_type")
    val rcdType: String? = null,

    @ColumnInfo(name = "rcd_poles")
    val rcdPoles: Int? = null,

    @ColumnInfo(name = "rcd_selectivity")
    val rcdSelectivity: String = "NONE",

    @ColumnInfo(name = "rcd_kind")
    val rcdKind: String? = null,

    @ColumnInfo(name = "rcd_source")
    val rcdSource: String = "LEGACY",

    @ColumnInfo(name = "manual_deviation_codes")
    val manualDeviationCodes: String = "",

    @ColumnInfo(name = "calculation_source")
    val calculationSource: String = "LEGACY",

    @ColumnInfo(name = "algorithm_version")
    val algorithmVersion: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),

    @ColumnInfo(name = "phase")
    val phase: String = Phase.A.name,

    // 🔹 Привязка к проекту (может быть null для мигрированных старых данных)
    @ColumnInfo(name = "project_id")
    val projectId: String
)
