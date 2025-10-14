package ru.mugalimov.volthome.data.remote.dto

// ---------------------------------------------------------
// Короткая карточка проекта (совпадает с сервером)
// ---------------------------------------------------------
data class ProjectShortDto(
    val id: String,
    val name: String,
    val note: String?,
    val version: Int,
    val updated_at: String,
    val is_deleted: Boolean
)

// ---------------------------------------------------------
// Полное дерево проекта (GET /v1/projects/{id})
// ---------------------------------------------------------
data class ProjectTreeDto(
    val project: ProjectShortDto,
    val rooms: List<RoomDto>,
    val groups: List<GroupDto>,
    val devices: List<DeviceDto>
)

// Сущности проекта (как приходят с сервера; поля — snake_case)
data class RoomDto(
    val id: String,
    val name: String,
    val meta: Map<String, Any?>? = null,
    val updated_at: String,
    val is_deleted: Boolean
)

data class GroupDto(
    val id: String,
    val name: String,
    val meta: Map<String, Any?>? = null,
    val updated_at: String,
    val is_deleted: Boolean
)

data class DeviceDto(
    val id: String,
    val name: String,
    val meta: Map<String, Any?>? = null,
    val updated_at: String,
    val is_deleted: Boolean
)

// ---------------------------------------------------------
// Delta (GET /v1/projects/{id}/delta?since=ISO)
// ---------------------------------------------------------
data class ProjectDeltaResponse(
    val rooms: DeltaBucket<RoomDto>,
    val groups: DeltaBucket<GroupDto>,
    val devices: DeltaBucket<DeviceDto>
)

data class DeltaBucket<T>(
    val upsert: List<T> = emptyList(),
    val delete: List<String> = emptyList() // UUID удалённых сущностей (tombstones)
)

// ---------------------------------------------------------
// Batch (POST /v1/projects/{id}/batch)
// Совместимо с твоими мапперами: id — nullable,
// room_id/group_id — nullable и опциональны
// ---------------------------------------------------------
data class ProjectBatchRequest(
    val baseVersion: Int? = null,
    val ops: Ops? = null
)

data class Ops(
    val rooms: OpBucket<RoomUpsert>? = null,
    val groups: OpBucket<GroupUpsert>? = null,
    val devices: OpBucket<DeviceUpsert>? = null
)

data class OpBucket<T>(
    val upsert: List<T> = emptyList(),
    val delete: List<String> = emptyList()
)

// Upsert-пэйлоады
data class RoomUpsert(
    val id: String? = null,                 // твои мапперы передают null → сервер сгенерит UUID
    val name: String,
    val meta: Map<String, Any?>? = null
)

data class GroupUpsert(
    val id: String? = null,
    val room_id: String? = null,
    val name: String,
    val meta: Map<String, Any?>? = null
)

data class DeviceUpsert(
    val id: String? = null,
    val group_id: String? = null,
    val name: String,
    val meta: Map<String, Any?>? = null
)

// Ответ batch
data class ProjectBatchResponse(
    val newVersion: Int,
    val conflicts: List<Conflict> = emptyList()
)

data class Conflict(
    val entity: String,                     // "rooms" | "groups" | "devices"
    val id: String,                         // UUID сущности
    val reason: String
)