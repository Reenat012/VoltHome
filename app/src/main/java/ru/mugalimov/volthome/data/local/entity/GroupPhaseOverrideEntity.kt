package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.mugalimov.volthome.domain.model.Phase

@Entity(
    tableName = "group_phase_overrides",
    foreignKeys = [
        ForeignKey(
            entity = CircuitGroupEntity::class,
            parentColumns = ["group_id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["group_id"]),
        Index(value = ["project_id"]),
        Index(value = ["project_id", "group_id"], unique = true)
    ]
)
data class GroupPhaseOverrideEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "group_id")
    val groupId: Long,

    @ColumnInfo(name = "phase")
    val phase: Phase,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)