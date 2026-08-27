package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "project_cable_defaults",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["project_id"])]
)
data class ProjectCableDefaultsEntity(
    @PrimaryKey val project_id: String,
    val material: String,
    val insulation: String,
    val installation_method: String,
    val ambient_temperature_c: Int,
    val grouped_circuits: Int,
    val max_voltage_drop_percent: Double
)
