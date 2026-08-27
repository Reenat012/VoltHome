package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "panel_layouts",
    primaryKeys = ["project_id"],
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
data class PanelLayoutEntity(
    val project_id: String,
    val snapshot_json: String,
    val schema_version: Int,
    val updated_at_epoch_ms: Long
)
