package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,        // UUIDv4
    val name: String,
    val note: String?,
    val version: Int,
    val updated_at: String,            // ISO8601 UTC
    val is_deleted: Boolean
)