package ru.mugalimov.volthome.data.remote.dto

// ---- Projects list ----
data class ProjectsListResponse(
    val items: List<ProjectShortDto>,
    val next: String?
)

data class ProjectShortDto(
    val id: String,
    val name: String,
    val note: String?,
    val version: Int,
    val updated_at: String,
    val is_deleted: Boolean
)

// ---- Create/Update ----
data class ProjectCreateRequest(
    val id: String? = null,
    val name: String,
    val note: String? = null
)

data class ProjectUpdateRequest(
    val name: String? = null,
    val note: String? = null
)

// ---- Project tree ----
data class ProjectTreeDto(
    val project: ProjectShortDto,
    val rooms: List<RoomDto>,
    val groups: List<GroupDto>,
    val devices: List<DeviceDto>
)

data class RoomDto(
    val id: String,
    val name: String,
    val meta: Map<String, Any?>?,
    val updated_at: String,
    val is_deleted: Boolean
)

data class GroupDto(
    val id: String,
    val name: String,
    val meta: Map<String, Any?>?,
    val updated_at: String,
    val is_deleted: Boolean
)

data class DeviceDto(
    val id: String,
    val name: String,
    val meta: Map<String, Any?>?,
    val updated_at: String,
    val is_deleted: Boolean
)

// ---- Delta/Batch ----
data class DeltaResponse(
    val rooms: DeltaBucket<RoomDto>,
    val groups: DeltaBucket<GroupDto>,
    val devices: DeltaBucket<DeviceDto>
)

data class DeltaBucket<T>(
    val upsert: List<T>,
    val delete: List<String>
)

data class BatchRequest(
    val baseVersion: Int? = null,
    val ops: Ops
)

data class Ops(
    val rooms: OpBucket<RoomUpsert>? = null,
    val groups: OpBucket<GroupUpsert>? = null,
    val devices: OpBucket<DeviceUpsert>? = null
)

data class OpBucket<T>(
    val upsert: List<T>? = null,
    val delete: List<String>? = null
)

data class RoomUpsert(
    val id: String? = null,
    val name: String,
    val meta: Map<String, Any?>? = null
)

data class GroupUpsert(
    val id: String? = null,
    val name: String,
    val meta: Map<String, Any?>? = null
)

data class DeviceUpsert(
    val id: String? = null,
    val name: String,
    val meta: Map<String, Any?>? = null
)

data class BatchResponse(
    val newVersion: Int,
    val conflicts: List<Conflict>
)

data class Conflict(
    val entity: String,
    val id: String,
    val reason: String
)