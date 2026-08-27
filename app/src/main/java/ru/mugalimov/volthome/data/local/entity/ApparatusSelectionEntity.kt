package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Проектный снимок выбранного оборудования.
 *
 * Полный снимок хранится JSON-ом, чтобы обновление встроенного каталога не
 * меняло уже согласованную комплектацию и цену проекта.
 */
@Entity(
    tableName = "apparatus_selections",
    primaryKeys = ["project_id", "slot_id"],
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
data class ApparatusSelectionEntity(
    val project_id: String,
    val slot_id: String,
    val snapshot_json: String,
    val catalog_version: String,
    val updated_at_epoch_ms: Long
)
