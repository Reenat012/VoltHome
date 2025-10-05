package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.util.Date

/**
 * Tombstone — пометка локально "удалённой" сущности.
 * Храним либо localId, либо serverUuid (или оба, если уже известны).
 * UI/DAO-квери должны исключать сущности, для которых есть tombstone.
 */
@Entity(
    tableName = "tombstones",
    indices = [
        Index(value = ["entity_type", "project_id"]),
        Index(value = ["entity_type", "local_id"]),
        Index(value = ["entity_type", "server_uuid"], unique = false)
    ]
)
data class TombstoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val project_id: String?,
    val entity_type: TombstoneEntityType,
    val local_id: Long?,       // локальный PK (rooms.id / devices.device_id / groups.group_id / projects.id отсутствует — проекты не LONG)
    val server_uuid: String?,  // серверный UUID, если уже известен
    val created_at: Date = Date()
)

enum class TombstoneEntityType {
    PROJECT,
    ROOM,
    DEVICE,
    GROUP
}