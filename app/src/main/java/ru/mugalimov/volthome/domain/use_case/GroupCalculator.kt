package ru.mugalimov.volthome.domain.use_case

import distributeGroupsBalanced
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
import kotlin.math.ceil

class GroupCalculator(
    private val roomRepository: RoomRepository,
    private val groupRepository: ExplicationRepository
) {

    private val roomSafetyProfiles = mapOf(
        RoomType.BATHROOM to SafetyProfile(rcdRequired = true),
        RoomType.KITCHEN to SafetyProfile(rcdRequired = true),
        RoomType.OUTDOOR to SafetyProfile(rcdRequired = true),
        RoomType.STANDARD to SafetyProfile(rcdRequired = false)
    )

    suspend fun calculateGroups(): GroupingResult {
        return try {
            val rooms = roomRepository.getRoomsWithDevices()
            var totalGroupNumber = 1
            val allGroups = mutableListOf<CircuitGroup>()

            // 1) Выделенные линии: только «явно тяжёлые»/по флагу
            rooms.forEach { roomWithDevices ->
                val room = roomWithDevices.room
                val safety = roomSafetyProfiles[room.roomType] ?: SafetyProfile()

                roomWithDevices.devices
                    .filter(::isHeavy)
                    .forEach { d ->
                        val profile = selectBreaker(d.nominalCurrent(), d.deviceType, d.hasMotor)
                        allGroups += createDedicatedGroup(
                            device = d,
                            profile = profile,
                            safetyProfile = safety,
                            groupNumber = totalGroupNumber++,
                            room = room
                        )
                    }
            }

            // 2) Обычные группы: FFD‑упаковка по типам, лимит ≤ номинала автомата
            rooms.forEach { roomWithDevices ->
                val room = roomWithDevices.room
                val safety = roomSafetyProfiles[room.roomType] ?: SafetyProfile()
                val commonDevices = roomWithDevices.devices.filterNot(::isHeavy)
                val byType = commonDevices.groupBy { it.deviceType }

                byType.forEach { (deviceType, devicesOfType) ->
                    val maxI = devicesOfType.maxOfOrNull { it.nominalCurrent() } ?: 0.0
                    val hasMotor = devicesOfType.any { it.hasMotor }
                    val profile = selectBreaker(maxI, deviceType, hasMotor)

                    val groups = createCircuitGroups(
                        devices = devicesOfType,
                        profile = profile,
                        safetyProfile = safety,
                        startGroupNumber = totalGroupNumber,
                        room = room
                    )
                    allGroups += groups
                    totalGroupNumber += groups.size
                }
            }

            // 3) Нормализуем номера ровно один раз
            val normalized = allGroups
                .sortedWith(compareBy<CircuitGroup> { it.roomId }.thenBy { it.groupNumber })
                .mapIndexed { idx, g -> g.copy(groupNumber = idx + 1) }

            // 4) Балансировка фаз (номера групп не меняем внутри)
            val distributed = distributeGroupsBalanced(normalized)

            // 5) Валидация до сохранения
            validateBeforeSave(distributed)

            // 6) Сохранение (желательно транзакционное в репозитории)
            saveGroupsWithDevices(distributed)

            GroupingResult.Success(ElectricalSystem(distributed))
        } catch (e: Exception) {
            GroupingResult.Error("Ошибка расчёта: ${e.message}")
        }
    }

    /** Явные критерии выделенных линий. */
    private fun isHeavy(d: DeviceEntity): Boolean =
        d.requiresDedicatedCircuit || when (d.deviceType) {
            DeviceType.OVEN,
            DeviceType.AIR_CONDITIONER,
            DeviceType.ELECTRIC_STOVE,
            DeviceType.HEAVY_DUTY -> true
            else -> false // розетки/освещение не уносим только из‑за мощности
        }

    /** Подбор автомата/кабеля/кривой по подгруппе. */
    private fun selectBreaker(nominalCurrent: Double, deviceType: DeviceType, hasMotor: Boolean): GroupProfile {
        val current = ceil(nominalCurrent).toInt()

        val minRatingByType = mapOf(
            DeviceType.LIGHTING to 10,
            DeviceType.SOCKET to 16,
            DeviceType.HEAVY_DUTY to 16,
            DeviceType.OVEN to 20,
            DeviceType.AIR_CONDITIONER to 20,
            DeviceType.ELECTRIC_STOVE to 25
        )

        val requiredMin = minRatingByType[deviceType] ?: 10
        val finalRequired = maxOf(current, requiredMin)

        // (rating A, cable mm^2, curve)
        val breakerOptions = listOf(
            Triple(10, 1.5, "B"),
            Triple(16, 2.5, "C"),
            Triple(20, 2.5, "C"),
            Triple(25, 4.0, "C"),
            Triple(32, 6.0, "C"),
            Triple(40, 10.0, "C"),
            Triple(50, 10.0, "D"),
            Triple(63, 16.0, "D")
        )

        val (rating, cable, baseCurve) = breakerOptions.firstOrNull { it.first >= finalRequired }
            ?: throw IllegalArgumentException("Нет подходящего автомата для ${finalRequired}А")

        // D — только для реально больших пусков; малые моторы оставляем на C
        val finalCurve = when {
            hasMotor && rating >= 25 -> "D"
            hasMotor -> "C"
            else -> baseCurve
        }

        return GroupProfile(
            maxCurrent = rating.toDouble(),
            breakerRating = rating,
            cableSection = cable,
            breakerType = finalCurve
        )
    }

    /** FFD‑упаковка устройств в группы с лимитом по номиналу автомата. */
    private fun createCircuitGroups(
        devices: List<DeviceEntity>,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        startGroupNumber: Int,
        room: RoomEntity
    ): List<CircuitGroup> {
        val sorted = devices.sortedByDescending { it.nominalCurrent() }
        val limit = profile.maxCurrent
        val eps = 1e-6

        // Одиночное устройство не должно превышать лимит группы
        val tooBig = sorted.firstOrNull { it.nominalCurrent() - limit > eps }
        require(tooBig == null) {
            "Устройство '${tooBig?.name}' в комнате '${room.name}' требует " +
                    "ток ${"%.2f".format(tooBig!!.nominalCurrent())} А > лимита группы ${limit} А. Нужна выделенная линия."
        }

        val bins = mutableListOf<MutableList<DeviceEntity>>()
        val sums = mutableListOf<Double>()

        for (d in sorted) {
            val cur = d.nominalCurrent()
            val idx = sums.indices.firstOrNull { sums[it] + cur <= limit + eps }
            if (idx != null) {
                bins[idx].add(d)
                sums[idx] += cur
            } else {
                bins += mutableListOf(d)
                sums += cur
            }
        }

        var number = startGroupNumber
        return bins.map { bin ->
            createGroup(
                devices = bin,
                profile = profile,
                safetyProfile = safetyProfile,
                groupNumber = number++,
                room = room
            )
        }
    }

    private fun createGroup(
        devices: List<DeviceEntity>,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        groupNumber: Int,
        room: RoomEntity
    ): CircuitGroup {
        val nominalCurrent = devices.sumOf { it.nominalCurrent() }
        return CircuitGroup(
            roomName = room.name,
            groupType = devices.first().deviceType,
            devices = devices.map { it.toDomainModel() },
            nominalCurrent = nominalCurrent,
            circuitBreaker = profile.breakerRating,
            cableSection = profile.cableSection,
            breakerType = profile.breakerType,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            groupNumber = groupNumber,
            roomId = room.id
        )
    }

    private fun createDedicatedGroup(
        device: DeviceEntity,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        groupNumber: Int,
        room: RoomEntity
    ): CircuitGroup {
        val nominalCurrent = device.nominalCurrent()
        return CircuitGroup(
            roomName = room.name,
            groupType = device.deviceType, // НЕ хардкодим HEAVY_DUTY
            devices = listOf(device.toDomainModel()),
            nominalCurrent = nominalCurrent,
            circuitBreaker = profile.breakerRating,
            cableSection = profile.cableSection,
            breakerType = profile.breakerType,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            groupNumber = groupNumber,
            roomId = room.id
        )
    }

    private suspend fun saveGroupsWithDevices(groups: List<CircuitGroup>) {
        // РЕКОМЕНДАЦИЯ: реализовать транзакционный метод в репозитории (replaceAll)
        groupRepository.deleteAllGroups()
        groupRepository.addGroup(groups)
    }

    private fun validateBeforeSave(groups: List<CircuitGroup>) {
        val eps = 1e-6
        groups.forEach { g ->
            requireNotNull(g.phase) { "Группа №${g.groupNumber} без фазы" }
            require(g.nominalCurrent <= g.circuitBreaker + eps) {
                "Группа №${g.groupNumber}: ${"%.2f".format(g.nominalCurrent)} А > ${g.circuitBreaker} А"
            }
            require(g.devices.isNotEmpty()) { "Группа №${g.groupNumber} не содержит устройств" }
            require(g.devices.all { it.deviceType == g.groupType }) {
                "Группа №${g.groupNumber}: тип группы ${g.groupType} не совпадает с типами устройств"
            }
        }
    }
}

// --- Extensions / мапперы ---

fun DeviceEntity.nominalCurrent(): Double =
    CurrentCalculator.calculateNominalCurrent(
        power = power.toDouble(),
        voltage = voltage.value.toDouble(),
        powerFactor = powerFactor,
        demandRatio = demandRatio,
        voltageType = voltage.type
    )

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
    roomId = roomId
)

fun List<DeviceEntity>.toDomainModels() = map { it.toDomainModel() }

fun CircuitGroupEntity.toDomainModel(devices: List<Device>) = CircuitGroup(
    groupId = 0L,
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