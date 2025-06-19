package ru.mugalimov.volthome.domain.use_case

import android.content.ContentValues.TAG
import android.util.Log
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.ElectricalSystem
import ru.mugalimov.volthome.domain.model.Room
import javax.inject.Inject

class GroupCalculator @Inject constructor(
    private val roomRepository: RoomRepository
) {
    // Основной метод расчета групп
    suspend fun calculateGroups(): ElectricalSystem {
        val rooms = roomRepository.getRoomsWithDevices()

        // Общий счетчик для номера группы
        var totalGroupNumber = 1

        Log.d(TAG, "Rooms в useCase $rooms")

        val allGroups = mutableListOf<CircuitGroup>()

        rooms.forEach { room ->
            // Обрабатываем каждую комнату
            val (lighting, sockets) = room.devices.partition { it.deviceType == DeviceType.LIGHTING }


            // Генерируем группы для освещения
            val lightingGroups =
                createCircuitGroups(
                    room = room.room.toDomainModelRoom(),
                    devices = lighting.toDomainModelListDevice(),
                    groupType = DeviceType.LIGHTING,
                    maxCurrent = 10.0,
                    breaker = 10,
                    cable = 1.5,
                    roomId = room.room.id,
                    currentGroupNumber = totalGroupNumber
                )

            // Добааляем группы для освещения в общий список групп
            allGroups.addAll(lightingGroups)

            // Обновляем счетчик на размер группы освещения
            totalGroupNumber += lightingGroups.size

            // Генерируем группы для розеток
            val socketGroups =
                createCircuitGroups(
                    room = room.room.toDomainModelRoom(),
                    devices = sockets.toDomainModelListDevice(),
                    groupType = DeviceType.SOCKET,
                    maxCurrent = 16.0,
                    breaker = 16,
                    cable = 2.5,
                    roomId = room.room.id,
                    currentGroupNumber = totalGroupNumber
                )

            // Добааляем группы для розеток в общий список групп
            allGroups.addAll(socketGroups)

            // Обновляем счетчик на размер группы розеток
            totalGroupNumber += socketGroups.size
        }

        return ElectricalSystem(allGroups)
    }


    // Создает группы для конкретного типа устройств в комнате
    private fun createCircuitGroups(
        room: Room,
        devices: List<Device>,
        groupType: DeviceType,
        maxCurrent: Double,
        breaker: Int,
        cable: Double,
        roomId: Long,
        currentGroupNumber: Int
    ): List<CircuitGroup> {
        // Проверка на превышение тока отдельными устройствами
        validateDevice(devices, groupType, maxCurrent)

        // Сортировка устройств по убыванию тока для оптимального распределения
        val sortedDevices = devices.sortedByDescending { it.current }

        val groups = mutableListOf<CircuitGroup>()
        var currentGroup = mutableListOf<Device>()
        var currentSum = 0.0
        var groupNumber = currentGroupNumber

        // Алгорит распределения устройства
        for (device in sortedDevices) {
            if (currentSum + device.current > maxCurrent) {
                groups.add(
                    createGroup(
                        room = room,
                        type = groupType,
                        devices = currentGroup,
                        current = currentSum,
                        breaker = breaker,
                        cable = cable,
                        groupNumber = groupNumber++,
                        roomId = roomId
                    )
                )
                currentGroup = mutableListOf()
                currentSum = 0.0
            }
            currentGroup.add(device)
            currentSum += device.current
        }

        // Добавляем последнюю группу
        if (currentGroup.isNotEmpty()) {
            groups.add(
                createGroup(
                    room = room,
                    type = groupType,
                    devices = currentGroup,
                    current = currentSum,
                    breaker = breaker,
                    cable = cable,
                    groupNumber = groupNumber,
                    roomId = roomId
                )
            )
        }

        return groups
    }

    private fun validateDevice(devices: List<Device>, type: DeviceType, maxCurrent: Double) {
        // Проверка устройств
        devices.forEach { device ->
            if (device.current > maxCurrent) {
                throw IllegalArgumentException(
                    "Устройство '${device.name}' превышает " +
                            "максимальный ток максимальный ток: ${device.current}А"
                )
            }
        }
    }

    private fun createGroup(
        room: Room,
        type: DeviceType,
        devices: List<Device>,
        current: Double,
        breaker: Int,
        cable: Double,
        groupNumber: Int,
        roomId: Long
    ): CircuitGroup {
        return CircuitGroup(
            roomName = room.name,
            groupType = type,
            devices = devices,
            nominalCurrent = current,
            circuitBreaker = breaker,
            cableSection = cable,
            groupNumber = groupNumber,
            roomId = roomId
        )
    }
}

private fun RoomEntity.toDomainModelRoom() = Room(
    id = id,
    name = name,
    createdAt = createdAt
)


private fun DeviceEntity.toDomainModelDevice() = Device(
    id = deviceId,
    name = name,
    power = power,
    demandRatio = demandRatio,
    voltage = voltage,
    createdAt = createdAt,
    roomId = roomId,
    deviceType = deviceType,
    powerFactor = powerFactor
)

private fun List<DeviceEntity>.toDomainModelListDevice(): List<Device> {
    return map { entity ->
        Device(
            id = entity.deviceId,
            name = entity.name,
            power = entity.power,
            voltage = entity.voltage,
            demandRatio = entity.demandRatio,
            createdAt = entity.createdAt,
            roomId = entity.roomId,
            deviceType = entity.deviceType,
            powerFactor = entity.powerFactor
        )
    }
}