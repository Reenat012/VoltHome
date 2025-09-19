package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_local_state")
data class ProjectLocalStateEntity(
    @PrimaryKey val project_id: String,
    val remote_version: Int,       // версия сервера
    val last_sync_at: String?,     // ISO8601 или null
    val has_local_changes: Boolean
)