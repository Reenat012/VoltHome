package ru.mugalimov.volthome.domain.mapper

import ru.mugalimov.volthome.data.local.entity.ProjectEntity
import ru.mugalimov.volthome.data.remote.dto.ProjectShortDto


fun ProjectShortDto.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    note = note,
    version = version,
    updated_at = updated_at,   // у тебя в Entity строка ISO — совместимо
    is_deleted = is_deleted
)