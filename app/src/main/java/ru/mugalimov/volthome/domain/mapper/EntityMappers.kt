package ru.mugalimov.volthome.data.mapper

import ru.mugalimov.volthome.data.local.entity.ProjectEntity
import ru.mugalimov.volthome.domain.model.Project
import ru.mugalimov.volthome.util.TimeUtils

fun nowIso(): String = TimeUtils.formatIso(TimeUtils.now())

fun ProjectEntity.toDomainProject(): Project = Project(
    id = id,
    name = name,
    note = note,
    version = version,
    updatedAt = updated_at,
    isDeleted = is_deleted
)
