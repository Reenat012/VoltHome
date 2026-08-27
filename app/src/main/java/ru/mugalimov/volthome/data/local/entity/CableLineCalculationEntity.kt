package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cable_line_calculations",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CircuitGroupEntity::class,
            parentColumns = ["group_id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["project_id"]), Index(value = ["group_id"])]
)
data class CableLineCalculationEntity(
    @PrimaryKey val group_id: Long,
    val project_id: String,
    val phase_mode: String,
    val load_current_a: Double,
    val breaker_a: Int,
    val length_m: Double?,
    val power_factor: Double,
    val material: String,
    val insulation: String,
    val installation_method: String,
    val ambient_temperature_c: Int,
    val grouped_circuits: Int,
    val max_voltage_drop_percent: Double,
    val manual_section_mm2: Double?,
    val phase_section_mm2: Double,
    val neutral_section_mm2: Double,
    val pe_section_mm2: Double,
    val cores: Int,
    val base_ampacity_a: Double,
    val installation_factor: Double,
    val temperature_factor: Double,
    val grouping_factor: Double,
    val corrected_ampacity_a: Double,
    val voltage_drop_v: Double?,
    val voltage_drop_percent: Double?,
    val status: String,
    val source: String,
    val checks: String,
    val algorithm_version: Int,
    val dataset_version: String,
    val updated_at_epoch_ms: Long
)
