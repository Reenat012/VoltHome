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


class GroupCalculator(
    private val roomRepository: RoomRepository,
    private val groupRepository: ExplicationRepository
) {
    // Обновленные профили групп
    private val groupProfiles = mapOf(
        DeviceType.LIGHTING to GroupProfile(10.0, 10, 1.5, "B"),
        DeviceType.SOCKET to GroupProfile(16.0, 16, 2.5, "C"),
        DeviceType.HEAVY_DUTY to GroupProfile(25.0, 25, 4.0, "D")
    )

    // Обновленные профили безопасности с током УЗО
    private val roomSafetyProfiles = mapOf(
        RoomType.BATHROOM to SafetyProfile(rcdRequired = true, rcdCurrent = 30),
        RoomType.KITCHEN to SafetyProfile(rcdRequired = true, rcdCurrent = 30),
        RoomType.OUTDOOR to SafetyProfile(rcdRequired = true, rcdCurrent = 30),
        RoomType.STANDARD to SafetyProfile(rcdRequired = false)
    )

    suspend fun calculateGroups(): GroupingResult {
        return try {
            val rooms = roomRepository.getRoomsWithDevices()
            var totalGroupNumber = 1
            val allGroups = mutableListOf<CircuitGroup>()

            // Шаг 1: Выделенные линии с учетом ограничения для розеток
            rooms.forEach { roomWithDevices ->
                val room = roomWithDevices.room
                val safetyProfile = roomSafetyProfiles[room.roomType] ?: SafetyProfile()

                roomWithDevices.devices
                    .filter { device ->
                        val threshold = when (device.voltage.type) {
                            VoltageType.AC_1PHASE -> 2300
                            VoltageType.AC_3PHASE -> 7000
                            else -> 2000
                        }
                        device.requiresDedicatedCircuit || device.power > threshold
                    }
                    .forEach { device ->
                        // Рассчитываем ток с учетом коэффициента спроса
                        val nominalCurrent = calculateCurrent(device)

                        val profile = when {
                            device.deviceType == DeviceType.HEAVY_DUTY -> {
                                // Для HEAVY_DUTY устройств динамически выбираем профиль по току
                                when {
                                    nominalCurrent > 25 -> GroupProfile(
                                        32.0,
                                        32,
                                        6.0,
                                        "D"
                                    ) // Усиленный профиль для мощных устройств
                                    else -> groupProfiles[DeviceType.HEAVY_DUTY]!! // Стандартный профиль 25A
                                }
                            }

                            device.requiresSocketConnection ->
                                groupProfiles[DeviceType.SOCKET]!! // Розеточные устройства
                            else -> {
                                // Обычные не-розеточные устройства
                                when {
                                    nominalCurrent > 25 -> GroupProfile(32.0, 32, 6.0, "D")
                                    nominalCurrent > 16 -> groupProfiles[DeviceType.HEAVY_DUTY]!!
                                    else -> groupProfiles[DeviceType.SOCKET]!!
                                }
                            }
                        }

                        // ДОБАВЛЕНА ПРОВЕРКА ДЛЯ ВЫДЕЛЕННЫХ ЛИНИЙ
                        validateDevices(device, profile)

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

            // Шаг 2: Стандартные группы с защитой от перегрузки
            rooms.forEach { roomWithDevices ->
                val room = roomWithDevices.room
                val safetyProfile = roomSafetyProfiles[room.roomType] ?: SafetyProfile()

                val devices = roomWithDevices.devices.filterNot { device ->
                    val threshold = when (device.voltage.type) {
                        VoltageType.AC_1PHASE -> 2300
                        VoltageType.AC_3PHASE -> 7000
                        else -> 2000
                    }
                    device.requiresDedicatedCircuit || device.power > threshold
                }

                val groupedDevices = devices.groupBy { it.deviceType }

                groupedDevices.forEach { (deviceType, typeDevices) ->
                    val profile = groupProfiles[deviceType] ?: groupProfiles[DeviceType.SOCKET]!!
                    val groups = createCircuitGroups(
                        devices = typeDevices,
                        profile = profile,
                        safetyProfile = safetyProfile,
                        startGroupNumber = totalGroupNumber,
                        room = room
                    )
                    allGroups.addAll(groups)
                    totalGroupNumber += groups.size
                }
            }

            saveGroupsWithDevices(allGroups)
            GroupingResult.Success(ElectricalSystem(allGroups))
        } catch (e: Exception) {
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
        val actualProfile = if (devices.firstOrNull()?.deviceType == DeviceType.SOCKET) {
            profile.copy(cableSection = 2.5) // Принудительно устанавливаем 2.5 мм²
        } else {
            profile
        }

        devices.forEach { validateDevices(it, profile) }
        val sortedDevices = devices.sortedByDescending { calculateCurrent(it) }
        val groups = mutableListOf<CircuitGroup>()
        var currentGroup = mutableListOf<DeviceEntity>()
        var currentSum = 0.0
        var groupNumber = startGroupNumber

        for (device in sortedDevices) {
            val nominalCurrent = calculateCurrent(device)

            // Защита от перегрузки группы (не более 80% автомата)
            val maxAllowedCurrent = profile.breakerRating * 0.8

            if (currentSum + nominalCurrent > maxAllowedCurrent) {
                if (currentGroup.isNotEmpty()) {
                    groups.add(
                        createGroup(
                            currentGroup,
                            actualProfile,
                            safetyProfile,
                            groupNumber++,
                            room
                        )
                    )
                    currentGroup = mutableListOf()
                    currentSum = 0.0
                }

                // Проверка для одиночного устройства
                if (nominalCurrent > maxAllowedCurrent) {
                    if (nominalCurrent > profile.breakerRating) {
                        Log.d(
                            TAG,
                            " \"Устройство '${device.name}' слишком мощное для автомата ${profile.breakerRating}A\""
                        )
                        throw IllegalArgumentException(
                            "Устройство '${device.name}' слишком мощное для автомата ${profile.breakerRating}A"
                        )
                    }
                    groups.add(
                        createGroup(
                            listOf(device),
                            profile,
                            safetyProfile,
                            groupNumber++,
                            room
                        )
                    )
                    continue
                }
            }

            currentGroup.add(device)
            currentSum += nominalCurrent
        }

        if (currentGroup.isNotEmpty()) {
            groups.add(createGroup(currentGroup, profile, safetyProfile, groupNumber, room))
        }

        return groups
    }

    private fun getPeakCurrent(device: DeviceEntity): Double {
        val base = calculateCurrent(device)
        return if (device.hasMotor) base * 5.0 else base
    }

    private fun determineBreakerType(
        devices: List<DeviceEntity>,
        profile: GroupProfile
    ): String {
        return when {
            devices.any { it.hasMotor } -> "D"
            profile.breakerType == "D" -> "D"
            else -> profile.breakerType
        }
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
        // ФИКС: Учитываем коэффициент спроса и коэффициент мощности
        val effectivePower = device.power.toDouble() * (device.demandRatio ?: 1.0)
        val voltage = (device.voltage.value.takeIf { it > 0 } ?: 230.0).toDouble()
        val powerFactor = (device.powerFactor ?: 1.0).coerceIn(0.8, 1.0)

        return when (device.voltage.type) {
            VoltageType.AC_1PHASE -> effectivePower / (voltage * powerFactor)
            VoltageType.AC_3PHASE -> effectivePower / (1.732 * voltage * powerFactor)
            VoltageType.DC -> effectivePower / voltage
            else -> effectivePower / voltage // Для неизвестных типов
        }
    }

    /**
     * Проверяет, что все устройства могут быть защищены автоматом из профиля
     */
    private fun validateDevices(device: DeviceEntity, profile: GroupProfile) {
        val nominalCurrent = calculateCurrent(device)
        val peakCurrent = getPeakCurrent(device)

        // 1. Проверка номинального тока (ПУЭ 3.1.10)
        if (nominalCurrent > profile.breakerRating) {
            Log.d(
                TAG, "\"Устройство '${device.name}' (${"%.2f".format(nominalCurrent)}A) \" +\n" +
                        "                        \"превышает номинал автомата ${profile.breakerRating}A\""
            )
            throw IllegalArgumentException(
                "Устройство '${device.name}' (${"%.2f".format(nominalCurrent)}A) " +
                        "превышает номинал автомата ${profile.breakerRating}A"
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
            Log.d(
                TAG, "                \"Пусковой ток устройства '${device.name}' (${
                    "%.2f".format(
                        peakCurrent
                    )
                }А) \" +\n" +
                        "                        \"превышает порог срабатывания автомата ${profile.breakerType} (${maxInstantaneousTrip}A)\""
            )
            throw IllegalArgumentException(
                "Пусковой ток устройства '${device.name}' (${"%.2f".format(peakCurrent)}А) " +
                        "превышает порог срабатывания автомата ${profile.breakerType} (${maxInstantaneousTrip}A)"
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
        val breakerType = determineBreakerType(devices, profile)

        // Для розеточных групп всегда тип SOCKET и кабель 2.5 мм²
        val isSocketGroup = devices.any { it.deviceType == DeviceType.SOCKET }

        val groupType = if (isSocketGroup) DeviceType.SOCKET else devices.first().deviceType

        val cableSection = when {
            isSocketGroup -> 2.5 // Для розеток всегда 2.5 мм²
            groupType == DeviceType.HEAVY_DUTY -> profile.cableSection
            else -> profile.cableSection
        }

        // Рассчитываем суммарный номинальный ток группы
        val nominalCurrent = devices.sumOf { device ->
            val effectivePower = device.power * device.demandRatio
            when (device.voltage.type) {
                VoltageType.AC_1PHASE -> effectivePower / (device.voltage.value * device.powerFactor)
                VoltageType.AC_3PHASE -> effectivePower / (1.732 * device.voltage.value * device.powerFactor)
                VoltageType.DC -> effectivePower / device.voltage.value.toDouble()
            }
        }

        // Проверка группового пускового тока
        val maxGroupPeak = devices.sumOf { device ->
            if (device.hasMotor) calculateCurrent(device) * 5 else calculateCurrent(device)
        }
        val maxTrip = when (breakerType) {
            "B" -> profile.breakerRating * 5
            "C" -> profile.breakerRating * 10
            "D" -> profile.breakerRating * 20
            else -> profile.breakerRating * 10
        }

        if (maxGroupPeak > maxTrip) {
            Log.d(
                TAG, " \"Группа $groupNumber: пусковой ток ${"%.1f".format(maxGroupPeak)}A \" +\n" +
                        "                        \"превышает порог ${maxTrip}A для автомата $breakerType${profile.breakerRating}\""
            )
            throw IllegalStateException(
                "Группа $groupNumber: пусковой ток ${"%.1f".format(maxGroupPeak)}A " +
                        "превышает порог ${maxTrip}A для автомата $breakerType${profile.breakerRating}"
            )
        }

        return CircuitGroup(
            roomName = room.name,
            groupType = groupType,
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

        val groupType = when {
            device.requiresSocketConnection -> DeviceType.SOCKET
            device.deviceType == DeviceType.HEAVY_DUTY -> DeviceType.HEAVY_DUTY
            else -> DeviceType.SOCKET // По умолчанию для совместимости
        }

        // ФИКС: Для розеточных устройств всегда кабель 2.5 мм²
        val cableSection = if (device.deviceType == DeviceType.HEAVY_DUTY) {
            // Для HEAVY_DUTY выбираем кабель по току
            when {
                nominalCurrent > 25 -> 6.0
                else -> 4.0
            }
        } else if (device.requiresSocketConnection) {
            2.5
        } else {
            when {
                nominalCurrent > 25 -> 6.0
                nominalCurrent > 16 -> 4.0
                else -> 2.5
            }
        }

        // Проверка по ПУЭ 7.1.79 для розеток
        // Определение типа автомата
        // ФИКС: Автоматический выбор типа автомата
        val breakerType = when {
            device.hasMotor -> "D"
            device.requiresSocketConnection -> "C"
            nominalCurrent > 16 -> "D"
            else -> "C"
        }

        // Проверка соответствия автомата
        val maxInstantaneousTrip = when (breakerType) {
            "B" -> profile.breakerRating * 5
            "C" -> profile.breakerRating * 10
            "D" -> profile.breakerRating * 20
            else -> profile.breakerRating * 10
        }

        // ФИКС: Проверка номинального тока с запасом 10%
        if (nominalCurrent > profile.breakerRating * 0.9) {
            Log.d(
                TAG, " \"Устройство '${device.name}' (${"%.1f".format(nominalCurrent)}A) \" +\n" +
                        "                        \"превышает 90% номинала автомата ${profile.breakerRating}A\""
            )
            throw IllegalArgumentException(
                "Устройство '${device.name}' (${"%.1f".format(nominalCurrent)}A) " +
                        "превышает 90% номинала автомата ${profile.breakerRating}A"
            )
        }

        if (peakCurrent > maxInstantaneousTrip) {
            Log.d(
                TAG,
                "  \"Пусковой ток устройства '${device.name}' (${"%.1f".format(peakCurrent)}А) \" +\n" +
                        "                        \"превышает порог срабатывания автомата $breakerType${profile.breakerRating}A\""
            )
            throw IllegalArgumentException(
                "Пусковой ток устройства '${device.name}' (${"%.1f".format(peakCurrent)}А) " +
                        "превышает порог срабатывания автомата $breakerType${profile.breakerRating}A"
            )
        }


        return CircuitGroup(
            roomName = room.name,
            groupType = groupType, // Корректный тип группы
            devices = listOf(device.toDomainModel()),
            nominalCurrent = nominalCurrent,
            circuitBreaker = profile.breakerRating,
            cableSection = cableSection, // Корректное сечение
            breakerType = breakerType,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            groupNumber = groupNumber,
            roomId = room.id
        )
    }
}

//// Добавим функцию для определения типа подключения устройства
//private fun isSocketConnection(device: DeviceEntity): Boolean {
//    return device.requiresSocketConnection // Только явное указание
//}
//
//// Функция, которая будет выбирать корректное сечение кабеля в зависимости от типа подключения
//private fun getAdjustedCableSection(baseSection: Double, current: Double): Double {
//    return when {
//        current > 25 -> 6.0   // Для токов >25A - кабель 6мм²
//        current > 16 -> 4.0   // Для токов >16A - кабель 4мм²
//        current > 10 -> 2.5   // Для токов >10A - кабель 2.5мм²
//        else -> 1.5           // Для остальных - 1.5мм²
//    }
//}
//
//// Обновленная функция выбора профиля
//private fun selectProfileForDevice(device: DeviceEntity, nominalCurrent: Double): GroupProfile {
//    // Определяем стандартные номиналы автоматов
//    val standardBreakerRatings = listOf(6, 10, 16, 20, 25, 32, 40, 50, 63, 80, 100)
//
//    // Находим минимально подходящий номинал
//    val selectedBreaker = standardBreakerRatings.firstOrNull { it >= nominalCurrent }
//        ?: standardBreakerRatings.maxOrNull()
//        ?: 100 // Фолбэк
//
//    // Выбираем сечение кабеля по току
//    val cableSection = when {
//        nominalCurrent <= 10 -> 1.5
//        nominalCurrent <= 16 -> 2.5
//        nominalCurrent <= 25 -> 4.0
//        nominalCurrent <= 32 -> 6.0
//        nominalCurrent <= 40 -> 10.0
//        nominalCurrent <= 50 -> 16.0
//        nominalCurrent <= 63 -> 16.0
//        nominalCurrent <= 80 -> 25.0
//        else -> 35.0
//    }
//
//    // Выбираем тип автомата
//    val breakerType = when {
//        device.hasMotor -> "D"
//        device.requiresSocketConnection -> "C"
//        selectedBreaker > 32 -> "D" // Для мощных устройств
//        else -> "C"
//    }
//
//    return GroupProfile(
//        maxCurrent = selectedBreaker.toDouble(),
//        breakerRating = selectedBreaker,
//        cableSection = cableSection,
//        breakerType = breakerType
//    )
//}

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
    requiresSocketConnection = requiresSocketConnection
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

