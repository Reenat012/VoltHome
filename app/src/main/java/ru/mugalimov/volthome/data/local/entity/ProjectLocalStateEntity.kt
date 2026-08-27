package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Локальное состояние проекта.
 *
 * Commit 1:
 * - persisted marker active_manual_project_id (SoT для single-manual globally)
 *
 * Commit X (manual ownership):
 * - persisted флаг manual_overrides_present: запрещает AUTO писать структуру групп
 * - persisted manual_lock_bootstrap_version: версия bootstrap/backfill (чтобы не считать каждый раз)
 */
@Entity(
    tableName = "project_local_state",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProjectLocalStateEntity(
    @PrimaryKey val project_id: String,
    // Persisted marker: значение projectId активного manual (может быть null)
    val active_manual_project_id: String? = null,

    // Persisted ownership-флаг: если true — AUTO не имеет права писать структуру (groups/joins/overrides)
    val manual_overrides_present: Boolean = false,

    // Persisted версия bootstrap/backfill для ownership (0 = не делали)
    val manual_lock_bootstrap_version: Int = 0
)
