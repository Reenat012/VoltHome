package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "project_setup",
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
data class ProjectSetupEntity(
    @PrimaryKey val project_id: String,
    val object_type: String,
    val phase_mode: String,
    val input_power_kw: Double? = null,
    val source_template_id: String? = null,
    val source_template_version: Int = 1,
    val wizard_completed: Boolean = false
)
