package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "groups",
    indices = [
        Index("project_id"),
        Index(value = ["project_id","updated_at"]),
        Index(value = ["project_id","is_deleted"])
    ]
)
data class GroupEntityExt(
    @PrimaryKey val id: String,
    val name: String,
    val project_id: String,
    val updated_at: Instant,
    val is_deleted: Boolean
)