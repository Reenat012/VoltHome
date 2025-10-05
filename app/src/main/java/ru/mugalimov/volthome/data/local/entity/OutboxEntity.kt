package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.util.Date

/**
 * Outbox — очередь изменений, которые нужно отправить на сервер при наличии сети.
 * Сохраняем чистый JSON payload (String), чтобы не плодить конвертеры.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["state", "created_at"]),        // выборка по состоянию
        Index(value = ["project_id", "state"]),        // группировка по проекту
        Index(value = ["group_key"], unique = false)   // идемпотентность пачек
    ]
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val project_id: String?,           // null допустим для операций уровня профиля/авторизации/списка проектов
    val op_type: OutboxOpType,         // тип операции (CRUD над Project/Room/Device/Group)
    val payload_json: String,          // тело операции в JSON (минимально необходимое для сборки батча)
    val requires_online: Boolean = true, // на будущее, если появятся операции, допускающие офлайн- подтверждение

    val state: OutboxState = OutboxState.PENDING,
    val attempt: Int = 0,
    val last_error: String? = null,
    val group_key: String? = null,     // идемпотентная "соль" — например "proj:ROOM_CREATE:localId=42"

    val created_at: Date = Date(),
    val updated_at: Date = Date()
)

/** Что именно мы пушим */
enum class OutboxOpType {
    // Projects
    PROJECT_CREATE,
    PROJECT_UPDATE,
    PROJECT_DELETE,

    // Rooms
    ROOM_CREATE,
    ROOM_UPDATE,
    ROOM_DELETE,

    // Devices
    DEVICE_CREATE,
    DEVICE_UPDATE,
    DEVICE_DELETE,

    // Groups (если используете)
    GROUP_CREATE,
    GROUP_UPDATE,
    GROUP_DELETE
}

/** Состояние записи в outbox */
enum class OutboxState {
    PENDING,        // ждёт отправки
    IN_PROGRESS,    // воркер взял в работу
    DONE,           // подтверждено сервером
    FAILED_RETRYABLE, // была ошибка, попробуем позже
    FAILED_FATAL    // невосстановимая ошибка, оставляем для диагностики (ручная чистка)
}