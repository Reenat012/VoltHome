package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Локальное состояние проекта.
 *
 * Commit 1:
 * - добавлен persisted marker active_manual_project_id (SoT для single-manual globally)
 */
@Entity(tableName = "project_local_state")
data class ProjectLocalStateEntity(
    @PrimaryKey val project_id: String,
    val remote_version: Int,       // версия сервера
    val last_sync_at: String?,     // ISO8601 или null
    val has_local_changes: Boolean,

    // Persisted marker: значение projectId активного manual (может быть null)
    val active_manual_project_id: String? = null
)