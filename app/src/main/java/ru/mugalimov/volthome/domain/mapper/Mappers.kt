package ru.mugalimov.volthome.domain.mapper

import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Load
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

// ──────────────────────────────────────────────────────────────────────────────
// DeviceEntity ↔ Device (одиночные объекты)
// ──────────────────────────────────────────────────────────────────────────────

fun DeviceEntity.toDomainDevice(): Device =
    Device(
        id = deviceId,
        name = name,
        power = power,
        voltage = voltage,                     // у тебя Voltage хранится через конвертер
        demandRatio = demandRatio,
        createdAt = createdAt,
        roomId = roomId,
        deviceType = deviceType,               // enum DeviceType в Entity
        powerFactor = powerFactor,
        hasMotor = hasMotor,
        requiresDedicatedCircuit = requiresDedicatedCircuit,
        requiresSocketConnection = requiresSocketConnection
    )

fun Device.toEntityDevice(): DeviceEntity =
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
        requiresSocketConnection = requiresSocketConnection
    )

// ──────────────────────────────────────────────────────────────────────────────
// Списки устройств
// ──────────────────────────────────────────────────────────────────────────────

fun List<DeviceEntity>.mapToDomainDevices(): List<Device> =
    map { it.toDomainDevice() }

fun List<Device>.mapToDeviceEntities(): List<DeviceEntity> =
    map { it.toEntityDevice() }

// ──────────────────────────────────────────────────────────────────────────────
/** CircuitGroupEntity ↔ CircuitGroup (одиночные объекты) */
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
        phase = phase.toPhaseOrDefaultA()                    // String → enum
    )

fun CircuitGroup.toEntityGroup(): CircuitGroupEntity =
    CircuitGroupEntity(
        groupId = groupId,
        groupNumber = groupNumber,
        roomId = roomId,
        roomName = roomName,
        groupType = groupType.name,                          // enum → String
        nominalCurrent = nominalCurrent,
        circuitBreaker = circuitBreaker,
        cableSection = cableSection,
        breakerType = breakerType,
        rcdRequired = rcdRequired,
        rcdCurrent = rcdCurrent,
        phase = phase.name                                   // enum → "A"/"B"/"C"
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

fun List<CircuitGroup>.mapToGroupEntities(): List<CircuitGroupEntity> =
    map { it.toEntityGroup() }

// ──────────────────────────────────────────────────────────────────────────────
/** CircuitGroupWithDevices (relation) → CircuitGroup (Domain) */
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

fun Room.toEntityRoom(): RoomEntity =
    RoomEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        roomType = roomType
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

fun List<Room>.mapToRoomEntities(): List<RoomEntity> =
    map { it.toEntityRoom() }

/**
 * Если нужно получить доменные Room из relation (RoomWithDevices),
 * добавь сюда свою Relation-модель и аналогичный маппер, например:
 *
 * data class RoomWithDevices(val room: RoomEntity, val devices: List<DeviceEntity>)
 *
 * fun RoomWithDevices.toDomainRoomFromRelation(): Room =
 *     room.toDomainRoom(devices.map { it.toDomainDevice() })
 *
 * fun List<RoomWithDevices>.mapToDomainRoomsFromRelations(): List<Room> =
 *     map { it.toDomainRoomFromRelation() }
 */

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

fun List<LoadEntity>.toDomainModelListLoad(): List<Load> {
    return map { entity ->
        Load(
            id = entity.id,
            name = entity.name,
            current = entity.currentRoom,
            sumPower = entity.powerRoom,
            countDevices = entity.countDevices,
            createdAt = entity.createdAt,
            roomId = entity.roomId
        )
    }
}

fun GroupDeviceJoin.toEntityJoin(): GroupDeviceJoin =
    GroupDeviceJoin(
        groupId  = groupId,
        deviceId = deviceId
    )

/** Маппер из DefaultDevice в DeviceCreateRequest для старого потока (сохраняем совместимость). */
private fun DefaultDevice.toCreateRequest(qty: Int): DeviceCreateRequest =
    DeviceCreateRequest(
        title = this.name,              // имя по каталогу (без кастомизации)
        type = this.deviceType,         // DeviceType
        count = qty.coerceAtLeast(1),
        ratedPowerW = this.power,       // всегда Вт
        powerFactor = this.powerFactor,
        demandRatio = this.demandRatio,
        voltage = this.voltage
    )