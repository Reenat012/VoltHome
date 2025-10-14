package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val project_id: String,
    val entity: String,     // "rooms" | "devices" | "groups"
    val entity_id: String,
    val reason: String,
    val created_at: String  // ISO8601
)