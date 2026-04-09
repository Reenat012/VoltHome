package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.mapper.toDomainDevice
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.GroupProfile
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.SafetyProfile
import ru.mugalimov.volthome.domain.policy.breaker.BreakerPolicyInput
import ru.mugalimov.volthome.domain.policy.breaker.BreakerPolicySelector
import ru.mugalimov.volthome.domain.use_case.PhaseDistributor.distributeGroupsBalancedWithLog
import java.nio.ByteBuffer
import java.security.MessageDigest

class GroupCalculator(
    private val projectId: String,
    private val roomRepository: RoomRepository,
    private val groupRepository: ExplicationRepository
) {

    // ✅ Единый selector policy
    private val breakerPolicySelector = BreakerPolicySelector()

    private val roomSafetyProfiles = mapOf(
        RoomType.BATHROOM to SafetyProfile(rcdRequired = true),
        RoomType.KITCHEN to SafetyProfile(rcdRequired = true),
        RoomType.OUTDOOR to SafetyProfile(rcdRequired = true),
        RoomType.STANDARD to SafetyProfile(rcdRequired = false)
    )

    suspend fun calculateGroups(mode: PhaseMode): GroupingResult {
        return try {
            val rooms = roomRepository.getRoomsWithDevicesByProject(projectId)

            CalculationTrace.log(
                stage = "GROUP_CALC_START",
                message = "projectId=$projectId mode=$mode rooms=${rooms.size}"
            )

            var totalGroupNumber = 1
            val allGroups = mutableListOf<CircuitGroup>()

            // 1) Выделенные линии
            rooms.forEach { roomWithDevices ->
                val room = roomWithDevices.room
                val safety = roomSafetyProfiles[room.roomType] ?: SafetyProfile()

                roomWithDevices.devices
                    .filter(::isHeavy)
                    .forEach { d ->
                        val canonicalDeviceCurrent = d.nominalCurrent()

                        CalculationTrace.log(
                            stage = "GROUP_CALC_DEVICE_CONTRIBUTION",
                            message =
                                "projectId=$projectId roomId=${room.id} room='${room.name}' " +
                                        "deviceId=${d.deviceId} device='${d.name}' type=${d.deviceType} " +
                                        "dedicated=true currentA=${CalculationTrace.f(canonicalDeviceCurrent)} " +
                                        "path=DeviceEntity.nominalCurrent()->CurrentCalculator.calculateNominalCurrent()"
                        )

                        val profile = selectBreaker(
                            nominalCurrent = canonicalDeviceCurrent,
                            deviceType = d.deviceType,
                            hasMotor = d.hasMotor
                        )

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
                    val maxI: Double = devicesOfType.maxOfOrNull { device: DeviceEntity ->
                        device.nominalCurrent()
                    } ?: 0.0
                    val hasMotor = devicesOfType.any { it.hasMotor }

                    CalculationTrace.log(
                        stage = "GROUP_CALC_COMMON_BUCKET",
                        message =
                            "projectId=$projectId roomId=${room.id} room='${room.name}' " +
                                    "deviceType=$deviceType devices=${devicesOfType.size} " +
                                    "maxCanonicalCurrentA=${CalculationTrace.f(maxI)} hasMotor=$hasMotor"
                    )

                    val profile = selectBreaker(
                        nominalCurrent = maxI,
                        deviceType = deviceType,
                        hasMotor = hasMotor
                    )

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

            val normalized = allGroups
                .sortedWith(compareBy<CircuitGroup> { it.roomId }.thenBy { it.groupNumber })
                .mapIndexed { idx, g -> g.copy(groupNumber = idx + 1) }

            CalculationTrace.log(
                stage = "GROUP_CALC_NORMALIZED",
                message =
                    "projectId=$projectId groupsBeforeIds=${allGroups.size} groupsAfterNormalize=${normalized.size}"
            )

            val withStableIds = assignStableGroupIds(normalized)

            CalculationTrace.log(
                stage = "GROUP_CALC_STABLE_IDS",
                message =
                    "projectId=$projectId groups=${withStableIds.size} sample=" +
                            withStableIds.take(10).joinToString { "g#${it.groupNumber}->${it.groupId}" }
            )

            val (distributed, decisionLog) =
                if (mode == PhaseMode.THREE) {
                    CalculationTrace.log(
                        stage = "GROUP_CALC_PHASE_BALANCING_INPUT",
                        message =
                            "projectId=$projectId mode=$mode canonicalWeightField=group.nominalCurrent " +
                                    "weights=" + withStableIds.joinToString { g ->
                                "g#${g.groupNumber}:${CalculationTrace.f(g.nominalCurrent)}A"
                            }
                    )
                    distributeGroupsBalancedWithLog(withStableIds)
                } else {
                    withStableIds.map { it.copy(phase = Phase.A) } to emptyList()
                }

            CalculationTrace.log(
                stage = "GROUP_CALC_PHASE_BALANCING_RESULT",
                message =
                    "projectId=$projectId distributed=${distributed.size} decisions=${decisionLog.size} " +
                            "phases=" + distributed.joinToString { g -> "g#${g.groupNumber}:${g.phase}" }
            )

            validateBeforeSave(distributed)

            CalculationTrace.log(
                stage = "GROUP_CALC_FINISH",
                message =
                    "projectId=$projectId groups=${distributed.size} result=SUCCESS"
            )

            GroupingResult.Success(
                system = ru.mugalimov.volthome.domain.model.ElectricalSystem(distributed),
                distributionDecisions = decisionLog
            )
        } catch (e: Exception) {
            CalculationTrace.log(
                stage = "GROUP_CALC_FINISH",
                message = "projectId=$projectId result=ERROR error='${e.message}'"
            )
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
            else -> false
        }

    /**
     * ✅ Единый policy-вход для выбора автомата.
     *
     * Здесь больше нет локальной эвристики.
     * AUTO path теперь пользуется тем же selector-ом, что и MANUAL.
     */
    private fun selectBreaker(
        nominalCurrent: Double,
        deviceType: DeviceType,
        hasMotor: Boolean
    ): GroupProfile {
        val result = breakerPolicySelector.select(
            BreakerPolicyInput(
                nominalCurrentA = nominalCurrent,
                deviceType = deviceType,
                hasMotor = hasMotor
            )
        )

        CalculationTrace.log(
            stage = "GROUP_CALC_LINE_SELECTION",
            message =
                "projectId=$projectId nominalCurrentA=${CalculationTrace.f(nominalCurrent)} " +
                        "deviceType=$deviceType hasMotor=$hasMotor " +
                        "selectedBreaker=${result.profile.breakerRating} " +
                        "selectedCable=${CalculationTrace.f(result.profile.cableSection)} " +
                        "selectedCurve=${result.profile.breakerType} " +
                        "floor=${result.reason.floorBreakerA} " +
                        "required=${result.reason.requiredBreakerA} " +
                        "curveRule=${result.reason.curveRule}"
        )

        return result.profile
    }

    /** FFD-упаковка устройств в группы с лимитом по номиналу автомата. */
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

        CalculationTrace.log(
            stage = "GROUP_CALC_FFD_START",
            message =
                "projectId=$projectId roomId=${room.id} room='${room.name}' " +
                        "devices=${devices.size} limitA=${CalculationTrace.f(limit)} " +
                        "breaker=${profile.breakerRating} cable=${CalculationTrace.f(profile.cableSection)} " +
                        "curve=${profile.breakerType}"
        )

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

        CalculationTrace.log(
            stage = "GROUP_CALC_FFD_RESULT",
            message =
                "projectId=$projectId roomId=${room.id} room='${room.name}' bins=${bins.size} " +
                        "binCurrents=" + sums.joinToString { CalculationTrace.f(it) }
        )

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

    /**
     * Сборка обычной группы AUTO path.
     */
    private fun createGroup(
        devices: List<DeviceEntity>,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        groupNumber: Int,
        room: RoomEntity
    ): CircuitGroup {
        val groupLoad = CurrentCalculator.calculateGroupLoad(
            devices.map { it.toLoadInput() }
        )

        val nominalCurrent = groupLoad.calculatedCurrentA
        val installedPowerW = groupLoad.installedPowerW.toInt()

        CalculationTrace.log(
            stage = "GROUP_CALC_GROUP_BUILT",
            message =
                "projectId=$projectId roomId=${room.id} room='${room.name}' groupNumber=$groupNumber " +
                        "kind=COMMON devices=${devices.size} deviceIds=${devices.joinToString { it.deviceId.toString() }} " +
                        "installedPowerW=$installedPowerW calculatedPowerW=${CalculationTrace.f(groupLoad.calculatedPowerW)} " +
                        "canonicalGroupCurrentA=${CalculationTrace.f(nominalCurrent)} " +
                        "lineBreaker=${profile.breakerRating} lineCable=${CalculationTrace.f(profile.cableSection)} " +
                        "lineCurve=${profile.breakerType}"
        )

        return CircuitGroup(
            roomName = room.name,
            groupType = devices.first().deviceType,
            devices = devices.map { it.toDomainDevice() },
            nominalCurrent = nominalCurrent,
            circuitBreaker = profile.breakerRating,
            cableSection = profile.cableSection,
            breakerType = profile.breakerType,
            whyBreakerSelected = profile.whyBreakerSelected,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            groupNumber = groupNumber,
            installedPowerW = installedPowerW,
            roomId = room.id
        )
    }

    /**
     * Сборка выделенной линии AUTO path.
     */
    private fun createDedicatedGroup(
        device: DeviceEntity,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        groupNumber: Int,
        room: RoomEntity
    ): CircuitGroup {
        val deviceLoad = CurrentCalculator.calculateDeviceLoad(device.toLoadInput())

        val nominalCurrent = deviceLoad.calculatedCurrentA
        val installedPowerW = deviceLoad.installedPowerW.toInt()

        CalculationTrace.log(
            stage = "GROUP_CALC_GROUP_BUILT",
            message =
                "projectId=$projectId roomId=${room.id} room='${room.name}' groupNumber=$groupNumber " +
                        "kind=DEDICATED deviceId=${device.deviceId} device='${device.name}' type=${device.deviceType} " +
                        "installedPowerW=$installedPowerW calculatedPowerW=${CalculationTrace.f(deviceLoad.calculatedPowerW)} " +
                        "canonicalGroupCurrentA=${CalculationTrace.f(nominalCurrent)} " +
                        "lineBreaker=${profile.breakerRating} lineCable=${CalculationTrace.f(profile.cableSection)} " +
                        "lineCurve=${profile.breakerType}"
        )

        return CircuitGroup(
            roomName = room.name,
            groupType = device.deviceType,
            devices = listOf(device.toDomainDevice()),
            nominalCurrent = nominalCurrent,
            circuitBreaker = profile.breakerRating,
            cableSection = profile.cableSection,
            breakerType = profile.breakerType,
            whyBreakerSelected = profile.whyBreakerSelected,
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

    private fun assignStableGroupIds(groups: List<CircuitGroup>): List<CircuitGroup> {
        if (groups.isEmpty()) return groups

        val badDeviceIdGroup = groups.firstOrNull { g -> g.devices.any { it.id <= 0L } }
        require(badDeviceIdGroup == null) {
            val badIds = badDeviceIdGroup!!.devices.filter { it.id <= 0L }.map { it.id }.take(20)
            "AUTO_CALC invariant failed: device.id must be > 0 for stable groupId. " +
                    "pid=$projectId groupNumber=${badDeviceIdGroup.groupNumber} roomId=${badDeviceIdGroup.roomId} badDeviceIds=$badIds"
        }

        val used = HashSet<Long>(groups.size * 2)

        return groups.map { g ->
            val baseKey = buildStableKey(g)

            var attempt = 0
            var newId: Long
            while (true) {
                val saltedKey = if (attempt == 0) baseKey else "$baseKey|salt=$attempt"
                newId = stablePositiveLong(saltedKey)
                if (used.add(newId)) break
                attempt++
                if (attempt > 10_000) {
                    throw IllegalStateException("Failed to allocate unique stable groupId for key=$baseKey pid=$projectId")
                }
            }

            g.copy(groupId = newId)
        }
    }

    private fun stableDevicesKey(g: CircuitGroup): String =
        g.devices
            .asSequence()
            .map { it.id }
            .sorted()
            .joinToString(separator = ",")

    private fun buildStableKey(g: CircuitGroup): String = buildString {
        append("pid=").append(projectId)
        append("|roomId=").append(g.roomId)
        append("|type=").append(g.groupType.name)
        append("|devs=").append(stableDevicesKey(g))
        append("|breaker=").append(g.circuitBreaker)
        append("|cable=").append(g.cableSection)
        append("|rcdReq=").append(if (g.rcdRequired) 1 else 0)
        append("|rcdCur=").append(g.rcdCurrent)
        append("|bt=").append(g.breakerType)
        append("|pW=").append(g.installedPowerW)
    }

    private fun stablePositiveLong(input: String): Long {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(input.toByteArray(Charsets.UTF_8))
        val value = ByteBuffer.wrap(hash, 0, 8).long
        val positive = value and Long.MAX_VALUE
        return if (positive == 0L) 1L else positive
    }
}

// --- Extensions / мапперы ---

/**
 * Маппинг DeviceEntity -> canonical LoadInput.
 */
private fun DeviceEntity.toLoadInput(): LoadInput =
    LoadInput(
        powerW = power.toDouble(),
        voltage = voltage.value.toDouble(),
        powerFactor = powerFactor,
        demandRatio = demandRatio,
        voltageType = voltage.type,
        label = name
    )

/**
 * Единый canonical AUTO path для вклада устройства в ток группы.
 */
fun DeviceEntity.nominalCurrent(): Double =
    CurrentCalculator.calculateDeviceLoad(toLoadInput()).calculatedCurrentA