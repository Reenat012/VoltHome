import android.content.ContentValues.TAG
import android.util.Log
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.ElectricalSystem
import ru.mugalimov.volthome.domain.model.GroupProfile
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.SafetyProfile
import ru.mugalimov.volthome.domain.model.VoltageType
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.min


class GroupCalculator(
    private val roomRepository: RoomRepository,
    private val groupRepository: ExplicationRepository
) {
    // Профили групп для разных типов устройств
    private val groupProfiles = mapOf(
        // Освещение: 10A автомат типа B, кабель 1.5 мм²
        DeviceType.LIGHTING to GroupProfile(10.0, 10, 1.5, "B"),

        // Розетки: 16A автомат типа C, кабель 2.5 мм²
        DeviceType.SOCKET to GroupProfile(16.0, 16, 2.5, "C"),

        // Мощные устройства: 25A автомат типа D, кабель 4.0 мм²
        DeviceType.HEAVY_DUTY to GroupProfile(25.0, 25, 4.0, "D")
    )

    // Профили безопасности для разных типов помещений
    private val roomSafetyProfiles = mapOf(
        RoomType.BATHROOM to SafetyProfile(rcdRequired = true),  // Ванная: УЗО обязательно
        RoomType.KITCHEN to SafetyProfile(rcdRequired = true),   // Кухня: УЗО обязательно
        RoomType.OUTDOOR to SafetyProfile(rcdRequired = true),   // Уличные розетки: УЗО
        RoomType.STANDARD to SafetyProfile(rcdRequired = false)  // Обычные: УЗО не требуется
    )

    /**
     * Основная функция расчета групп
     * Возвращает sealed class с результатом (успех или ошибка)
     */
    suspend fun calculateGroups(): GroupingResult {
        return try {
            // Шаг 1: Получаем все комнаты с устройствами из базы данных
            val rooms = roomRepository.getRoomsWithDevices()

            var totalGroupNumber = 1  // Счетчик групп для сквозной нумерации
            val allGroups = mutableListOf<CircuitGroup>()

            // Шаг 2: Обработка ВЫДЕЛЕННЫХ ЛИНИЙ для мощных устройств
            rooms.forEach { roomWithDevices ->
                val room = roomWithDevices.room
                Log.d("GroupCalc", "${room.roomType}")

                val safetyProfile = roomSafetyProfiles[room.roomType] ?: SafetyProfile()


                roomWithDevices.devices
                    .filter { device ->
                        // Фильтруем устройства, требующие выделенной линии:
                        // - Явно помеченные requiresDedicatedCircuit
                        // - Мощностью более 2000 Вт
                        device.requiresDedicatedCircuit || device.power > 2000
                    }
                    .forEach { device ->
                        // Создаем отдельную группу для каждого такого устройства
                        val profile = groupProfiles[DeviceType.HEAVY_DUTY]!!
                        allGroups.add(
                            createDedicatedGroup(
                                device = device,
                                profile = profile,
                                safetyProfile = safetyProfile,
                                groupNumber = totalGroupNumber++,
                                room = room
                            )
                        )
                    }
            }

            // Шаг 3: Обработка СТАНДАРТНЫХ ГРУПП (освещение, розетки)
            rooms.forEach { roomWithDevices ->
                val room = roomWithDevices.room
                val safetyProfile = roomSafetyProfiles[room.roomType] ?: SafetyProfile()

                // Фильтруем устройства, не требующие выделенной линии
                val devices = roomWithDevices.devices.filterNot {
                    it.requiresDedicatedCircuit || it.power > 2000
                }

                // Группируем устройства по их типу (освещение, розетки)
                val groupedDevices = devices.groupBy { it.deviceType }

                // Обрабатываем каждую группу устройств отдельно
                groupedDevices.forEach { (deviceType, typeDevices) ->
                    // Получаем профиль для данного типа устройств
                    val profile = groupProfiles[deviceType] ?: groupProfiles[DeviceType.SOCKET]!!

                    // Создаем группы устройств с учетом параметров профиля
                    val groups = createCircuitGroups(
                        devices = typeDevices,
                        profile = profile,
                        safetyProfile = safetyProfile,
                        startGroupNumber = totalGroupNumber,
                        room = room
                    )

                    // Добавляем созданные группы в общий список
                    allGroups.addAll(groups)
                    totalGroupNumber += groups.size
                }
            }
            // Сохраняем группы и связи
            saveGroupsWithDevices(allGroups)

            // Возвращаем успешный результат с рассчитанными группами
            GroupingResult.Success(ElectricalSystem(allGroups))
        } catch (e: Exception) {
            // Возвращаем ошибку с сообщением
            GroupingResult.Error("Ошибка расчета: ${e.message}")
        }
    }

    private suspend fun saveGroupsToDatabase(groups: List<CircuitGroup>) {
        // Преобразуем в Entity и сохраняем
        groupRepository.addGroup(groups)

        // Обновляем связи устройств с группами
        groups.forEach { group ->
            group.devices.forEach { device ->
                groupRepository.addDeviceToGroup(device.id, group.groupId)
            }
        }
    }

    private suspend fun saveGroupsWithDevices(groups: List<CircuitGroup>) {
        // Очищаем старые группы и связи
        groupRepository.deleteAllGroups()

        // Сохраняем новые группы
        groupRepository.addGroup(groups)
    }

    /**
     * Создает группы для списка устройств с учетом ограничений профиля
     */
    private fun createCircuitGroups(
        devices: List<DeviceEntity>,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        startGroupNumber: Int,
        room: RoomEntity
    ): List<CircuitGroup> {
        devices.forEach { validateDevices(it, profile) }
        val sortedDevices =
            devices.sortedByDescending { calculateCurrent(it) } // Используем номинальный ток для сортировки
        val groups = mutableListOf<CircuitGroup>()
        var currentGroup = mutableListOf<DeviceEntity>()
        var currentSum = 0.0
        var groupNumber = startGroupNumber

        for (device in sortedDevices) {
            val nominalCurrent = calculateCurrent(device) // Номинальный ток (без пусковых)
            val peakCurrent = getPeakCurrent(device)      // Пиковый ток (с пусковыми)

            // 1. Проверка, помещается ли устройство в текущую группу
            if (currentSum + nominalCurrent > profile.maxCurrentWithReserve) {
                // Сохраняем текущую группу, если она не пустая
                if (currentGroup.isNotEmpty()) {
                    groups.add(
                        createGroup(
                            currentGroup,
                            profile,
                            safetyProfile,
                            groupNumber++,
                            room
                        )
                    )
                    currentGroup = mutableListOf()
                    currentSum = 0.0
                }

                // 2. Проверка, помещается ли устройство в ПУСТУЮ группу
                if (nominalCurrent > profile.maxCurrentWithReserve) {
                    // Устройство слишком мощное даже для отдельной группы
                    if (peakCurrent > profile.breakerRating) {
                        throw IllegalArgumentException(
                            "Устройство '${device.name}' (${device.power}W) слишком мощное " +
                                    "для автомата ${profile.breakerRating}A. Требуется минимум ${
                                        ceil(
                                            peakCurrent
                                        ).toInt()
                                    }A"
                        )
                    }

                    // Создаем отдельную группу для одного устройства
                    groups.add(
                        createGroup(
                            listOf(device),
                            profile,
                            safetyProfile,
                            groupNumber++,
                            room
                        )
                    )
                    continue  // Переходим к следующему устройству
                }
            }

            // 3. Добавляем устройство в текущую группу
            currentGroup.add(device)
            currentSum += nominalCurrent
        }

        // Добавляем последнюю группу
        if (currentGroup.isNotEmpty()) {
            groups.add(createGroup(currentGroup, profile, safetyProfile, groupNumber, room))
        }

        return groups
    }

    private fun getPeakCurrent(device: DeviceEntity): Double {
        val base = calculateCurrent(device)
        return if (device.hasMotor) base * 5.0 else base
    }

    private fun determineBreakerType(devices: List<DeviceEntity>, baseType: String): String {
        // Если есть устройства с двигателем - используем тип D
        return if (devices.any { it.hasMotor }) "D" else baseType
    }

    /**
     * Рассчитывает эффективный ток устройства с учетом:
     * - Коэффициента спроса
     * - Коэффициента мощности
     * - Пусковых токов для двигателей
     */
    private fun getEffectiveCurrent(device: DeviceEntity): Double {
        // Базовый расчет тока: P * k_s / (U * cosφ)
        val baseCurrent = calculateCurrent(device)

        // Учет пусковых токов для устройств с двигателями
        return when {
            device.hasMotor -> baseCurrent * 5.0  // Пусковой ток в 5 раз выше номинала
            else -> baseCurrent
        }
    }

    private fun calculateCurrent(device: DeviceEntity): Double {
        // Базовый расчет без пусковых токов
        return when (device.voltage.type) {
            VoltageType.AC_1PHASE -> device.power / (device.voltage.value * device.powerFactor)
            VoltageType.AC_3PHASE -> device.power / (1.732 * device.voltage.value * device.powerFactor)
            VoltageType.DC -> device.power / device.voltage.value.toDouble()
        }
    }

    /**
     * Проверяет, что все устройства могут быть защищены автоматом из профиля
     */
    private fun validateDevices(device: DeviceEntity, profile: GroupProfile) {
        val nominalCurrent = calculateCurrent(device)
        val peakCurrent = getPeakCurrent(device)

        // 1. Проверка номинального тока
        if (nominalCurrent > profile.maxCurrent) {
            throw IllegalArgumentException(
                "Устройство '${device.name}' требует автомата минимум на ${ceil(nominalCurrent).toInt()}A"
            )
        }

        // 2. Проверка пускового тока
        val maxInstantaneousTrip = when (profile.breakerType) {
            "B" -> profile.breakerRating * 5
            "C" -> profile.breakerRating * 10
            "D" -> profile.breakerRating * 20
            else -> profile.breakerRating * 10
        }

        if (peakCurrent > maxInstantaneousTrip) {
            throw IllegalArgumentException(
                "Автомат не защищает устройство: пусковой ток $peakCurrent А слишком высок для типа ${profile.breakerType}"
            )
        }
    }

    /**
     * Создает группу из списка устройств
     */
    private fun createGroup(
        devices: List<DeviceEntity>,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        groupNumber: Int,
        room: RoomEntity
    ): CircuitGroup {
        val breakerType = determineBreakerType(devices, profile.breakerType)

        // Используем первое устройство для определения типа подключения группы
        val isSocketGroup = devices.firstOrNull()?.let { isSocketConnection(it) } ?: false

        // Рассчитываем суммарный номинальный ток группы
        val nominalCurrent = devices.sumOf { device ->
            (device.power * device.demandRatio) /
                    (device.voltage.value * device.powerFactor)
        }

        // Корректируем сечение кабеля
        val cableSection = if (isSocketGroup) {
            min(profile.cableSection, 2.5)
        } else {
            profile.cableSection
        }

        return CircuitGroup(
            roomName = room.name,
            groupType = devices.first().deviceType,
            devices = devices.map { it.toDomainModel() },
            nominalCurrent = nominalCurrent,
            circuitBreaker = profile.breakerRating,
            cableSection = cableSection,
            breakerType = breakerType,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            groupNumber = groupNumber,
            roomId = room.id
        )
    }

    /**
     * Создает выделенную группу для одного мощного устройства
     */
    private fun createDedicatedGroup(
        device: DeviceEntity,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        groupNumber: Int,
        room: RoomEntity
    ): CircuitGroup {
        val nominalCurrent = calculateCurrent(device)
        val peakCurrent = getPeakCurrent(device)

        // Проверяем, выдержит ли автомат пусковой ток
        val maxInstantaneousTrip = when (profile.breakerType) {
            "B" -> profile.breakerRating * 5
            "C" -> profile.breakerRating * 10
            "D" -> profile.breakerRating * 20
            else -> profile.breakerRating * 10
        }

        if (peakCurrent > maxInstantaneousTrip) {
            throw IllegalArgumentException(
                "Автомат ${profile.breakerType}${profile.breakerRating}A не защищает устройство " +
                        "'${device.name}': пусковой ток $peakCurrent А превышает порог срабатывания $maxInstantaneousTrip А"
            )
        }

        // Корректируем сечение кабеля для розеточных подключений
        val cableSection = getAdjustedCableSection(profile.cableSection, device)

        return CircuitGroup(
            roomName = room.name,
            groupType = DeviceType.HEAVY_DUTY,
            devices = listOf(device.toDomainModel()),
            nominalCurrent = nominalCurrent,
            circuitBreaker = profile.breakerRating,
            cableSection = cableSection,
            breakerType = profile.breakerType,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            groupNumber = groupNumber,
            roomId = room.id
        )
    }
}

// Добавим функцию для определения типа подключения устройства
private fun isSocketConnection(device: DeviceEntity): Boolean {
    // Для розеточных устройств или явно помеченных как требующих розетку
    return device.deviceType == DeviceType.SOCKET ||
            device.requiresSocketConnection
}

// Функция, которая будет выбирать корректное сечение кабеля в зависимости от типа подключения
private fun getAdjustedCableSection(
    baseSection: Double,
    device: DeviceEntity
): Double {
    return when {
        isSocketConnection(device) && baseSection > 2.5 -> 2.5
        else -> baseSection
    }
}

// Файл: data/mapper/DeviceMapper.kt
fun DeviceEntity.toDomainModel() = Device(
    id = deviceId,
    name = name,
    power = power,
    voltage = voltage,
    demandRatio = demandRatio,
    powerFactor = powerFactor,
    hasMotor = hasMotor,
    requiresDedicatedCircuit = requiresDedicatedCircuit,
    deviceType = deviceType,
    roomId = roomId,
    requiresSocketConnection = when (deviceType) {
        DeviceType.SOCKET -> true
        DeviceType.LIGHTING -> false
        DeviceType.HEAVY_DUTY -> false // По умолчанию мощные устройства без розетки
    }
)

fun List<DeviceEntity>.toDomainModels() = map { it.toDomainModel() }

fun CircuitGroupEntity.toDomainModel(devices: List<Device>) = CircuitGroup(
    groupId = groupId,
    groupNumber = groupNumber,
    roomName = roomName,
    roomId = roomId,
    groupType = DeviceType.valueOf(groupType),
    devices = devices,
    nominalCurrent = nominalCurrent,
    circuitBreaker = circuitBreaker,
    cableSection = cableSection,
    breakerType = breakerType,
    rcdRequired = rcdRequired,
    rcdCurrent = rcdCurrent
)


