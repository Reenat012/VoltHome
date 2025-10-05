package ru.mugalimov.volthome.data.sync.outbox

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import java.util.*

/** Базовые payload'ы для outbox. Pusher потом переведёт их в server DTO/batch. */

val gson by lazy { Gson() }
fun Any.toJson(): String = gson.toJson(this)

/* ---------- Projects ---------- */

data class ProjectCreatePayload(
    @SerializedName("local_id") val localId: String,
    val name: String,
    val note: String?
)

data class ProjectUpdatePayload(
    val id: String,         // может быть draft-*, может быть серверный UUID
    val name: String?,
    val note: String?
)

data class ProjectDeletePayload(
    val id: String          // если draft-*, pusher просто снесёт локальные данные и скипнет сервер
)

/* ---------- Rooms ---------- */

data class RoomCreatePayload(
    val projectId: String,
    @SerializedName("local_id") val localId: Long,
    val name: String,
    val roomType: RoomType,
    val createdAt: Long
)

data class RoomUpdatePayload(
    val projectId: String,
    val localId: Long,
    val name: String,
    val roomType: RoomType
)

data class RoomDeletePayload(
    val projectId: String,
    val localId: Long?,         // если знаем только локальный id
    val serverUuid: String?     // если уже маппился на сервер
)

/* ---------- Devices ---------- */

data class DeviceCreatePayload(
    val projectId: String,
    @SerializedName("local_id") val localId: Long,
    val roomLocalId: Long,
    val name: String,
    val power: Int,
    val voltage: Voltage,
    val demandRatio: Double,
    val createdAt: Long,
    val deviceType: DeviceType,
    val powerFactor: Double,
    val hasMotor: Boolean,
    val requiresDedicatedCircuit: Boolean,
    val requiresSocketConnection: Boolean
)

data class DeviceUpdatePayload(
    val projectId: String,
    val localId: Long,
    val roomLocalId: Long?,
    val name: String,
    val power: Int,
    val voltage: Voltage,
    val demandRatio: Double,
    val deviceType: DeviceType,
    val powerFactor: Double,
    val hasMotor: Boolean,
    val requiresDedicatedCircuit: Boolean,
    val requiresSocketConnection: Boolean
)

data class DeviceDeletePayload(
    val projectId: String,
    val localId: Long?,         // если знаем только локальный id
    val serverUuid: String?     // если уже маппился на сервер
)