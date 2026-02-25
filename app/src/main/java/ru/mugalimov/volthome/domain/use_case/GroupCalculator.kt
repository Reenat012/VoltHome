package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.mapper.toDomainDevice
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.ElectricalSystem
import ru.mugalimov.volthome.domain.model.GroupProfile
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.SafetyProfile
import ru.mugalimov.volthome.domain.use_case.PhaseDistributor.distributeGroupsBalanced
import ru.mugalimov.volthome.domain.use_case.PhaseDistributor.distributeGroupsBalancedWithLog
import java.nio.ByteBuffer
import java.security.MessageDigest
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

    suspend fun calculateGroups(mode: PhaseMode): GroupingResult {
        return try {
            val rooms = roomRepository.getRoomsWithDevices()
            var totalGroupNumber = 1
            val allGroups = mutableListOf<CircuitGroup>()

            // 1) Выделенные линии
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

            // 2) Обычные группы: FFD по типам
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

            // 3) Нормализуем номера один раз
            val normalized = allGroups
                .sortedWith(compareBy<CircuitGroup> { it.roomId }.thenBy { it.groupNumber })
                .mapIndexed { idx, g -> g.copy(groupNumber = idx + 1) }

            // 3.1) ✅ Генерируем стабильные groupId (валидные, уникальные, детерминированные)
            // Важно: НЕ используем groupNumber как ключ — он может измениться при добавлении/удалении устройств.
            val withStableIds = assignStableGroupIds(normalized)

            // 4) Балансировка фаз / режим 1 фаза
            val (distributed, decisionLog) =
                if (mode == PhaseMode.THREE) {
                    distributeGroupsBalancedWithLog(withStableIds)
                } else {
                    withStableIds.map { it.copy(phase = Phase.A) } to emptyList()
                }

            // 5) Валидация до сохранения
            validateBeforeSave(distributed)

            // 6) НИКАКОГО сохранения здесь (если сохраняешь в VM)
            GroupingResult.Success(
                system = ElectricalSystem(distributed),
                distributionDecisions = decisionLog
            )
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
    private fun selectBreaker(
        nominalCurrent: Double,
        deviceType: DeviceType,
        hasMotor: Boolean
    ): GroupProfile {
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
        val installedPowerW = devices.sumOf { it.power } // ✅ домен, не UI
        return CircuitGroup(
            roomName = room.name,
            groupType = devices.first().deviceType,
            devices = devices.map { it.toDomainDevice() },
            nominalCurrent = nominalCurrent,
            circuitBreaker = profile.breakerRating,
            cableSection = profile.cableSection,
            breakerType = profile.breakerType,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            groupNumber = groupNumber,
            installedPowerW = installedPowerW,
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
        val installedPowerW = device.power
        return CircuitGroup(
            roomName = room.name,
            groupType = device.deviceType, // НЕ хардкодим HEAVY_DUTY
            devices = listOf(device.toDomainDevice()),
            nominalCurrent = nominalCurrent,
            circuitBreaker = profile.breakerRating,
            cableSection = profile.cableSection,
            breakerType = profile.breakerType,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            groupNumber = groupNumber,
            installedPowerW = installedPowerW,
            roomId = room.id
        )
    }

    private fun validateBeforeSave(groups: List<CircuitGroup>) {
        val eps = 1e-6
        groups.forEach { g ->
            // phase в модели не nullable, но оставляем проверку как инвариант (если модель поменяют — поймаем сразу)
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

// ------------------------------------------------------------------------
// ✅ Stable groupId allocator (Commit B1)
// ------------------------------------------------------------------------

    /**
     * Генерирует стабильные groupId для AUTO-результата.
     *
     * Гарантии:
     * - id > 0
     * - unique в пределах списка
     * - stable (детерминированно): одинаковая структура => одинаковые id
     *
     * Ключ НЕ использует groupNumber (он может меняться при добавлении/удалении устройств).
     * Основа ключа:
     *   roomId + groupType + sorted(deviceIds) + breaker/cable/rcd
     */
    private fun assignStableGroupIds(groups: List<CircuitGroup>): List<CircuitGroup> {
        if (groups.isEmpty()) return groups

        // Сохраняем порядок стабильным: от этого зависит детерминированное разрешение коллизий.
        val ordered = groups.sortedWith(
            compareBy<CircuitGroup> { it.roomId }
                .thenBy { it.groupType.name }
                .thenBy { stableDevicesKey(it) }
                .thenBy { it.circuitBreaker }
                .thenBy { it.cableSection }
                .thenBy { it.rcdRequired }
                .thenBy { it.rcdCurrent }
        )

        val used = HashSet<Long>(ordered.size * 2)

        return ordered.map { g ->
            val baseKey = buildStableKey(g)

            // На случай коллизий добавляем соль детерминированно.
            var attempt = 0
            var newId: Long
            do {
                val saltedKey = if (attempt == 0) baseKey else "$baseKey|salt=$attempt"
                newId = stablePositiveLong(saltedKey)
                attempt++
                if (attempt > 1000) {
                    // Это уже "вселенной конец": значит ключи реально совпали массово.
                    throw IllegalStateException("Failed to allocate unique stable groupId for key=$baseKey")
                }
            } while (!used.add(newId))

            // ВАЖНО: меняем ТОЛЬКО groupId. Всё остальное — как рассчитано.
            g.copy(groupId = newId)
        }
    }

    /**
     * Стабильный ключ устройств:
     * - используем device.id (локальный id > 0 после insert)
     * - сортируем, чтобы порядок не влиял
     */
    private fun stableDevicesKey(g: CircuitGroup): String =
        g.devices
            .asSequence()
            .map { it.id }
            .sorted()
            .joinToString(separator = ",")

    /**
     * Базовый stable key группы.
     * Здесь сознательно нет groupNumber.
     */
    private fun buildStableKey(g: CircuitGroup): String = buildString {
        append("roomId=").append(g.roomId)
        append("|type=").append(g.groupType.name)
        append("|devs=").append(stableDevicesKey(g))
        append("|breaker=").append(g.circuitBreaker)
        append("|cable=").append(g.cableSection)
        append("|rcdReq=").append(g.rcdRequired)
        append("|rcdCur=").append(g.rcdCurrent)
    }

    /**
     * Детерминированный Long > 0 на основе строки.
     * Используем SHA-256 и берём первые 8 байт.
     */
    private fun stablePositiveLong(input: String): Long {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(input.toByteArray(Charsets.UTF_8))
        val value = ByteBuffer.wrap(hash, 0, 8).long
        val positive = value and Long.MAX_VALUE
        return if (positive == 0L) 1L else positive
    }
}


// --- Extensions / мапперы ---

fun DeviceEntity.nominalCurrent(): Double =
    CurrentCalculator.calculateNominalCurrent(
        power = power.toDouble(),
        voltage = (voltage.value.takeIf { it > 0 } ?: 230).toDouble(),
        powerFactor = powerFactor,
        demandRatio = demandRatio,
        voltageType = voltage.type
    )

