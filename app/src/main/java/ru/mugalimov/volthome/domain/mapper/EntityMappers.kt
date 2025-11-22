package ru.mugalimov.volthome.data.mapper

import ru.mugalimov.volthome.data.local.entity.*
import ru.mugalimov.volthome.data.remote.dto.*
import ru.mugalimov.volthome.domain.model.*
import ru.mugalimov.volthome.util.TimeUtils
import java.util.Date

// ---------------------------
// server -> local (delta/pull)
// ---------------------------
// ВАЖНО: серверные DTO не содержат ваших доменных полей и локальных числовых PK.
// Поэтому:
//  - отдаем Room автогенерацию PK: id/groupId/deviceId = 0
//  - заполняем безопасные дефолты для доменных полей
//  - createdAt ставим "сейчас", так как DTO не несут локального createdAt
//  - projectId в ваших сущностях отсутствует — не маппим

fun RoomDto.toEntity(): RoomEntity =
    RoomEntity(
        id = 0L,                          // автоинкремент (Room поставит реальный ID)
        name = name,
        createdAt = Date(),               // нет в DTO — используем текущую дату
        roomType = RoomType.STANDARD        // выбери дефолт, если есть иной — подставь
    )

fun GroupDto.toEntity(roomId: Long = 0L, roomNameFallback: String = ""): CircuitGroupEntity =
    CircuitGroupEntity(
        groupId = 0L,                     // автоинкремент
        groupNumber = 0,                  // нет в DTO — безопасный дефолт
        roomId = roomId,                  // если знаешь привязку — передай фактический roomId
        roomName = if (roomNameFallback.isNotBlank()) roomNameFallback else name,
        groupType = "GENERIC",
        nominalCurrent = 0.0,
        circuitBreaker = 0,
        cableSection = 0.0,
        breakerType = "C",
        rcdRequired = false,
        rcdCurrent = 30,
        createdAt = Date(),
        phase = Phase.A.name
    )

fun DeviceDto.toEntity(roomId: Long = 0L): DeviceEntity =
    DeviceEntity(
        deviceId = 0L,                    // автоинкремент
        name = name,
        power = 0,                        // нет данных в DTO → ставим дефолт
        voltage = Voltage(
            value = 220,
            type = VoltageType.AC_1PHASE
        ),
        demandRatio = 1.0,
        createdAt = Date(),
        roomId = roomId,
        deviceType = DeviceType.OTHER,    // дефолт
        powerFactor = 1.0,
        hasMotor = false,
        requiresDedicatedCircuit = false,
        requiresSocketConnection = true
    )

// ---------------------------
// local -> server (push/batch)
// ---------------------------
// Серверу не нужны ваши доменные поля — он ждёт только name (+ необязательный id, meta).

fun RoomEntity.toUpsert(): RoomUpsert = RoomUpsert(
    id = null,            // локальный PK Long серверу не подходит; пусть сгенерит UUID
    name = name,
    meta = null           // при желании упакуй доменные поля в meta
)

fun CircuitGroupEntity.toUpsert(): GroupUpsert = GroupUpsert(
    id = null,
    name = roomName,      // на стороне сервера это просто «название»
    meta = mapOf(
        "groupNumber" to groupNumber,
        "groupType" to groupType,
        "phase" to phase
    )
)

//fun DeviceEntity.toUpsert(): DeviceUpsert = DeviceUpsert(
//    id = null,
//    name = name,
//    meta = mapOf(
//        "power" to power,
//        "voltage" to voltage.value,
//        "deviceType" to deviceType.name,
//        "powerFactor" to powerFactor,
//        "hasMotor" to hasMotor
//    )
//)

// -------- утилиты --------

fun nowIso(): String = TimeUtils.formatIso(TimeUtils.now())

fun ProjectEntity.toDomainProject(): Project =
    Project(
        id = id,
        name = name,
        note = note,
        version = version,
        updatedAt = updated_at,
        isDeleted = is_deleted
    )