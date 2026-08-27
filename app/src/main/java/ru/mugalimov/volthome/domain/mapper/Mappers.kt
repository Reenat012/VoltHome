package ru.mugalimov.volthome.domain.mapper

import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomWithDevice
import ru.mugalimov.volthome.domain.model.RoomWithDevicesEntity
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest

// ──────────────────────────────────────────────────────────────────────────────
// Вспомогательные парсеры для enum-строк
// ──────────────────────────────────────────────────────────────────────────────

private fun String?.toPhaseOrDefaultA(): Phase =
    runCatching { Phase.valueOf(this?.trim()?.uppercase().orEmpty()) }.getOrElse { Phase.A }

private fun String?.toDeviceTypeOrDefaultSocket(): DeviceType =
    runCatching { DeviceType.valueOf(this?.trim()?.uppercase().orEmpty()) }.getOrElse { DeviceType.SOCKET }

private fun String?.toCalculationSource(): CalculationSource =
    runCatching { CalculationSource.valueOf(this?.trim()?.uppercase().orEmpty()) }
        .getOrElse { CalculationSource.LEGACY }

// ──────────────────────────────────────────────────────────────────────────────
// DeviceEntity ↔ Device (одиночные объекты)
// ──────────────────────────────────────────────────────────────────────────────

fun DeviceEntity.toDomainDevice(): Device =
    Device(
        id = deviceId,
        name = name,
        power = power,
        voltage = voltage, // Voltage хранится через конвертер
        demandRatio = demandRatio,
        createdAt = createdAt,
        roomId = roomId,
        deviceType = deviceType, // enum DeviceType в Entity
        powerFactor = powerFactor,
        hasMotor = hasMotor,
        requiresDedicatedCircuit = requiresDedicatedCircuit,
        requiresSocketConnection = requiresSocketConnection
    )

/**
 * КРИТИЧНО:
 * Нельзя создавать DeviceEntity без projectId, иначе в мультипроектности девайсы "утекут" в никуда.
 */
@Deprecated(
    message = "ОПАСНО: toEntityDevice() без projectId. Используй toEntityDevice(projectId).",
    replaceWith = ReplaceWith("toEntityDevice(projectId)"),
    level = DeprecationLevel.ERROR
)
fun Device.toEntityDevice(): DeviceEntity =
    throw IllegalStateException("Use toEntityDevice(projectId)")

fun Device.toEntityDevice(projectId: String): DeviceEntity =
    DeviceEntity(
        deviceId = id,
        name = name,
        power = power,
        voltage = voltage,
        demandRatio = demandRatio,
        createdAt = createdAt,
        roomId = roomId,
        deviceType = deviceType,
        powerFactor = powerFactor,
        hasMotor = hasMotor,
        requiresDedicatedCircuit = requiresDedicatedCircuit,
        requiresSocketConnection = requiresSocketConnection,
        projectId = projectId
    )

// ──────────────────────────────────────────────────────────────────────────────
// Списки устройств
// ──────────────────────────────────────────────────────────────────────────────

fun List<DeviceEntity>.mapToDomainDevices(): List<Device> =
    map { it.toDomainDevice() }

/**
 * КРИТИЧНО:
 * Нельзя маппить List<Device> → List<DeviceEntity> без projectId.
 */
@Deprecated(
    message = "ОПАСНО: mapToDeviceEntities() без projectId. Используй mapToDeviceEntities(projectId).",
    replaceWith = ReplaceWith("mapToDeviceEntities(projectId)"),
    level = DeprecationLevel.ERROR
)
fun List<Device>.mapToDeviceEntities(): List<DeviceEntity> =
    throw IllegalStateException("Use mapToDeviceEntities(projectId)")

fun List<Device>.mapToDeviceEntities(projectId: String): List<DeviceEntity> =
    map { it.toEntityDevice(projectId) }

// ──────────────────────────────────────────────────────────────────────────────
// CircuitGroupEntity ↔ CircuitGroup (одиночные объекты)
// ──────────────────────────────────────────────────────────────────────────────

fun CircuitGroupEntity.toDomainGroup(devices: List<Device> = emptyList()): CircuitGroup =
    CircuitGroup(
        groupId = groupId,
        groupNumber = groupNumber,
        roomName = roomName,
        roomId = roomId,
        groupType = groupType.toDeviceTypeOrDefaultSocket(), // String → enum
        devices = devices,
        nominalCurrent = nominalCurrent,
        circuitBreaker = circuitBreaker,
        cableSection = cableSection,
        breakerType = breakerType,
        rcdRequired = rcdRequired,
        rcdCurrent = rcdCurrent,
        rcdReasonCodes = rcdReasonCodes.split(',').map(String::trim).filter(String::isNotBlank),
        rcdSpec = if (rcdRequired) {
            ru.mugalimov.volthome.domain.model.protection.RcdSpec(
                kind = runCatching {
                    ru.mugalimov.volthome.domain.model.protection.RcdKind.valueOf(rcdKind.orEmpty())
                }.getOrDefault(ru.mugalimov.volthome.domain.model.protection.RcdKind.RCD),
                ratedCurrentA = rcdNominalCurrent,
                leakageCurrentMa = rcdCurrent,
                type = runCatching {
                    ru.mugalimov.volthome.domain.model.incomer.RcdType.valueOf(rcdType.orEmpty())
                }.getOrDefault(ru.mugalimov.volthome.domain.model.incomer.RcdType.A),
                poles = rcdPoles ?: if (phase.toPhaseOrDefaultA() == Phase.THREE_PHASE) 4 else 2,
                selectivity = runCatching {
                    ru.mugalimov.volthome.domain.model.incomer.RcdSelectivity.valueOf(rcdSelectivity)
                }.getOrDefault(ru.mugalimov.volthome.domain.model.incomer.RcdSelectivity.NONE),
                source = runCatching {
                    CalculationSource.valueOf(rcdSource)
                }.getOrDefault(CalculationSource.LEGACY)
            )
        } else null,
        manualDeviationCodes = manualDeviationCodes
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank),
        calculationSource = calculationSource.toCalculationSource(),
        algorithmVersion = algorithmVersion,
        installedPowerW = ru.mugalimov.volthome.domain.use_case.CircuitLoadCalculator
            .calculate(devices)
            .installedPowerW
            .toInt(),
        phase = phase.toPhaseOrDefaultA() // String → enum
    )

/**
 * КРИТИЧНО:
 * Нельзя создавать CircuitGroupEntity без projectId, иначе группы "утекут" между проектами.
 */
@Deprecated(
    message = "ОПАСНО: toEntityGroup() без projectId. Используй toEntityGroup(projectId).",
    replaceWith = ReplaceWith("toEntityGroup(projectId)"),
    level = DeprecationLevel.ERROR
)
fun CircuitGroup.toEntityGroup(): CircuitGroupEntity =
    throw IllegalStateException("Use toEntityGroup(projectId)")

fun CircuitGroup.toEntityGroup(projectId: String): CircuitGroupEntity =
    CircuitGroupEntity(
        groupId = groupId,
        groupNumber = groupNumber,
        roomId = roomId,
        roomName = roomName,
        groupType = groupType.name,
        nominalCurrent = nominalCurrent,
        circuitBreaker = circuitBreaker,
        cableSection = cableSection,
        breakerType = breakerType,
        rcdRequired = rcdRequired,
        rcdCurrent = rcdCurrent,
        rcdReasonCodes = rcdReasonCodes.joinToString(","),
        rcdNominalCurrent = rcdSpec?.ratedCurrentA,
        rcdType = rcdSpec?.type?.name,
        rcdPoles = rcdSpec?.poles,
        rcdSelectivity = rcdSpec?.selectivity?.name ?: "NONE",
        rcdKind = rcdSpec?.kind?.name,
        rcdSource = rcdSpec?.source?.name ?: calculationSource.name,
        manualDeviationCodes = manualDeviationCodes.joinToString(","),
        calculationSource = calculationSource.name,
        algorithmVersion = algorithmVersion,
        phase = phase.name,
        projectId = projectId
    )

// ──────────────────────────────────────────────────────────────────────────────
// Списки групп (Entity ↔ Domain)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Преобразует список групп-Entity в доменные группы, подтягивая устройства из карты.
 * key = groupId, value = список доменных устройств этой группы.
 */
fun List<CircuitGroupEntity>.mapToDomainGroups(
    groupsDevices: Map<Long, List<Device>> = emptyMap()
): List<CircuitGroup> =
    map { e -> e.toDomainGroup(groupsDevices[e.groupId].orEmpty()) }

/**
 * КРИТИЧНО:
 * Нельзя маппить List<CircuitGroup> → List<CircuitGroupEntity> без projectId.
 */
@Deprecated(
    message = "ОПАСНО: mapToGroupEntities() без projectId. Используй mapToGroupEntities(projectId).",
    replaceWith = ReplaceWith("mapToGroupEntities(projectId)"),
    level = DeprecationLevel.ERROR
)
fun List<CircuitGroup>.mapToGroupEntities(): List<CircuitGroupEntity> =
    throw IllegalStateException("Use mapToGroupEntities(projectId)")

fun List<CircuitGroup>.mapToGroupEntities(projectId: String): List<CircuitGroupEntity> =
    map { it.toEntityGroup(projectId) }

// ──────────────────────────────────────────────────────────────────────────────
// CircuitGroupWithDevices (relation) → CircuitGroup (Domain)
// ──────────────────────────────────────────────────────────────────────────────

fun CircuitGroupWithDevices.toDomainGroupFromRelation(): CircuitGroup {
    val domainDevices = devices.map { it.toDomainDevice() }
    return group.toDomainGroup(domainDevices)
}

fun List<CircuitGroupWithDevices>.mapToDomainGroupsFromRelations(): List<CircuitGroup> =
    map { it.toDomainGroupFromRelation() }

// ──────────────────────────────────────────────────────────────────────────────
// RoomEntity ↔ Room (одиночные объекты)
// ──────────────────────────────────────────────────────────────────────────────

fun RoomEntity.toDomainRoom(devices: List<Device> = emptyList()): Room =
    Room(
        id = id,
        name = name,
        createdAt = createdAt,
        roomType = roomType,
        devices = devices
    )

fun Room.toEntityRoom(projectId: String): RoomEntity =
    RoomEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        roomType = roomType,
        projectId = projectId
    )

// ──────────────────────────────────────────────────────────────────────────────
// Списки комнат
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Преобразует список комнат-Entity в доменные комнаты, подтягивая устройства из карты.
 * key = roomId, value = список доменных устройств комнаты.
 */
fun List<RoomEntity>.mapToDomainRooms(
    devicesByRoom: Map<Long, List<Device>> = emptyMap()
): List<Room> =
    map { re -> re.toDomainRoom(devicesByRoom[re.id].orEmpty()) }

fun List<Room>.mapToRoomEntities(projectId: String): List<RoomEntity> =
    map { it.toEntityRoom(projectId) }

// ──────────────────────────────────────────────────────────────────────────────
// Совместимость со старыми моделями RoomWithDevicesEntity
// ──────────────────────────────────────────────────────────────────────────────

fun RoomWithDevicesEntity.toDomainModelGroup(): Room {
    return Room(
        id = room.id,
        name = room.name,
        createdAt = room.createdAt,
        devices = devices.map { it.toDomainDevice() },
        roomType = room.roomType
    )
}

fun List<RoomWithDevicesEntity>.toDomainModelListRoomWithDevices(): List<RoomWithDevice> {
    return map { entity ->
        RoomWithDevice(
            room = entity.room,
            devices = entity.devices
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Join
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Этот "маппер" не делает ничего (GroupDeviceJoin → GroupDeviceJoin).
 * Оставляем только для обратной совместимости, но запрещаем использовать дальше.
 */
@Deprecated(
    message = "Бесполезно: GroupDeviceJoin -> GroupDeviceJoin. Удали использование.",
    level = DeprecationLevel.WARNING
)
fun GroupDeviceJoin.toEntityJoin(): GroupDeviceJoin =
    GroupDeviceJoin(
        groupId = groupId,
        deviceId = deviceId
    )

// ──────────────────────────────────────────────────────────────────────────────
// DefaultDevice → DeviceCreateRequest (старый поток, совместимость)
// ──────────────────────────────────────────────────────────────────────────────

/** Маппер из DefaultDevice в DeviceCreateRequest для старого потока (сохраняем совместимость). */
private fun DefaultDevice.toCreateRequest(qty: Int): DeviceCreateRequest =
    DeviceCreateRequest(
        title = this.name, // имя по каталогу (без кастомизации)
        type = this.deviceType, // DeviceType
        count = qty.coerceAtLeast(1),
        ratedPowerW = this.power, // всегда Вт
        powerFactor = this.powerFactor,
        demandRatio = this.demandRatio,
        voltage = this.voltage,
        hasMotor = this.hasMotor,
        requiresDedicatedCircuit = this.requiresDedicatedCircuit,
        requiresSocketConnection = this.requiresSocketConnection
    )
