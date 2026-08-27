package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.mapper.toDomainDevice
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.CalculationAlgorithm
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.GroupProfile
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.SafetyProfile
import ru.mugalimov.volthome.domain.policy.compatibility.CircuitCompatibilityPolicy
import ru.mugalimov.volthome.domain.policy.breaker.BreakerPolicyDefaults
import ru.mugalimov.volthome.domain.policy.line.LinePolicyInput
import ru.mugalimov.volthome.domain.policy.line.LinePolicySelector
import ru.mugalimov.volthome.domain.policy.protection.RcdDeviceInput
import ru.mugalimov.volthome.domain.policy.protection.RcdSelectionPolicy
import ru.mugalimov.volthome.domain.policy.protection.RcdSpecFactory
import ru.mugalimov.volthome.domain.use_case.PhaseDistributor.distributeGroupsBalancedWithLog
import java.nio.ByteBuffer
import java.security.MessageDigest

class GroupCalculator(
    private val projectId: String,
    private val roomRepository: RoomRepository,
    private val groupRepository: ExplicationRepository
) {

    private data class GroupBucketKey(
        val deviceType: DeviceType,
        val voltageType: ru.mugalimov.volthome.domain.model.VoltageType,
        val requiresSocketConnection: Boolean
    )

    // ✅ Единый selector линии: breaker -> cable
    private val linePolicySelector = LinePolicySelector()

    private val rcdSelectionPolicy = RcdSelectionPolicy()

    suspend fun calculateGroups(mode: PhaseMode): GroupingResult {
        return try {
            val rooms = roomRepository.getRoomsWithDevicesByProject(projectId)
            if (mode == PhaseMode.SINGLE) {
                val threePhaseDevice = rooms
                    .asSequence()
                    .flatMap { it.devices.asSequence() }
                    .firstOrNull { it.voltage.type == ru.mugalimov.volthome.domain.model.VoltageType.AC_3PHASE }
                require(threePhaseDevice == null) {
                    "Устройство '${threePhaseDevice?.name}' требует трёхфазную сеть"
                }
            }

            CalculationTrace.log(
                stage = "GROUP_CALC_START",
                message = "projectId=$projectId mode=$mode rooms=${rooms.size}"
            )

            val unsupportedDeviceIds = rooms
                .asSequence()
                .flatMap { it.devices.asSequence() }
                .filter { it.installedCurrent() > BreakerPolicyDefaults.maxSupportedNominalA }
                .map { it.deviceId }
                .toSet()

            if (unsupportedDeviceIds.isNotEmpty()) {
                CalculationTrace.log(
                    stage = "GROUP_CALC_UNSUPPORTED_DEVICES",
                    message =
                        "projectId=$projectId maxSupportedCurrentA=${BreakerPolicyDefaults.maxSupportedNominalA} " +
                            "deviceIds=${unsupportedDeviceIds.sorted().joinToString()} action=leave_unassigned"
                )
            }

            var totalGroupNumber = 1
            val allGroups = mutableListOf<CircuitGroup>()

            // 1) Выделенные линии
            rooms.forEach { roomWithDevices ->
                val room = roomWithDevices.room
                roomWithDevices.devices
                    .filterNot { it.deviceId in unsupportedDeviceIds }
                    .filter(::isHeavy)
                    .forEach { d ->
                        val safety = safetyProfileFor(room.roomType, listOf(d))
                        val canonicalDeviceCurrent = d.nominalCurrent()
                        val lineDeviceCurrent = d.installedCurrent()

                        CalculationTrace.log(
                            stage = "GROUP_CALC_DEVICE_CONTRIBUTION",
                            message =
                                "projectId=$projectId roomId=${room.id} room='${room.name}' " +
                                        "deviceId=${d.deviceId} device='${d.name}' type=${d.deviceType} " +
                                        "dedicated=true currentA=${CalculationTrace.f(canonicalDeviceCurrent)} " +
                                        "path=DeviceEntity.nominalCurrent()->CurrentCalculator.calculateNominalCurrent()"
                        )

                        val profile = selectLineProfile(
                            nominalCurrent = lineDeviceCurrent,
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
                val commonDevices = roomWithDevices.devices
                    .filterNot { it.deviceId in unsupportedDeviceIds }
                    .filterNot(::isHeavy)
                // Устройства с разным числом фаз и типом подключения не могут
                // оказаться на одной групповой линии, даже если deviceType совпадает.
                val byType = commonDevices.groupBy {
                    GroupBucketKey(
                        deviceType = it.deviceType,
                        voltageType = it.voltage.type,
                        requiresSocketConnection = it.requiresSocketConnection
                    )
                }

                byType.forEach { (bucket, devicesOfType) ->
                    val deviceType = bucket.deviceType
                    val safety = safetyProfileFor(room.roomType, devicesOfType)
                    val maxI: Double = devicesOfType.maxOfOrNull { device: DeviceEntity ->
                        device.installedCurrent()
                    } ?: 0.0
                    val hasMotor = devicesOfType.any { it.hasMotor }

                    CalculationTrace.log(
                        stage = "GROUP_CALC_COMMON_BUCKET",
                        message =
                            "projectId=$projectId roomId=${room.id} room='${room.name}' " +
                                    "deviceType=$deviceType devices=${devicesOfType.size} " +
                                    "maxCanonicalCurrentA=${CalculationTrace.f(maxI)} hasMotor=$hasMotor"
                    )

                    val profile = selectLineProfile(
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

            val finalGroups = distributed.map { group ->
                group.copy(
                    rcdSpec = RcdSpecFactory.createGroupRecommendation(
                        required = group.rcdRequired,
                        leakageCurrentMa = group.rcdCurrent,
                        breakerRatingA = group.circuitBreaker,
                        phase = group.phase,
                        source = CalculationSource.AUTO
                    )
                )
            }

            validateBeforeSave(finalGroups, mode)

            CalculationTrace.log(
                stage = "GROUP_CALC_FINISH",
                message =
                    "projectId=$projectId groups=${finalGroups.size} result=SUCCESS"
            )

            GroupingResult.Success(
                system = ru.mugalimov.volthome.domain.model.ElectricalSystem(finalGroups),
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
        d.requiresDedicatedCircuit || d.installedCurrent() > 16.0 || when (d.deviceType) {
            DeviceType.OVEN,
            DeviceType.AIR_CONDITIONER,
            DeviceType.ELECTRIC_STOVE,
            DeviceType.HEAVY_DUTY -> true
            else -> false
        }

    /**
     * УЗО выбирается по фактическому назначению и окончанию групповой линии:
     * особое помещение, бытовая розетка или нагрузка с розеточным подключением.
     */
    private fun safetyProfileFor(
        roomType: RoomType,
        devices: List<DeviceEntity>
    ): SafetyProfile {
        val decision = rcdSelectionPolicy.select(
            roomType = roomType,
            devices = devices.map {
                RcdDeviceInput(
                    deviceType = it.deviceType,
                    requiresSocketConnection = it.requiresSocketConnection
                )
            }
        )

        return SafetyProfile(
            rcdRequired = decision.required,
            rcdCurrent = decision.leakageCurrentMa ?: 30,
            reasons = decision.reasons
        )
    }

    /**
     * ✅ Единый policy-вход для выбора полной линии.
     *
     * Важно:
     * - сначала breaker;
     * - потом cable;
     * - AUTO path теперь использует тот же line selector, что и MANUAL.
     */
    private fun selectLineProfile(
        nominalCurrent: Double,
        deviceType: DeviceType,
        hasMotor: Boolean
    ): GroupProfile {
        val result = linePolicySelector.select(
            LinePolicyInput(
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
                        "breakerFloor=${result.profile.whyBreakerSelected?.floorBreakerA} " +
                        "breakerRequired=${result.profile.whyBreakerSelected?.requiredBreakerA} " +
                        "breakerRule=${result.profile.whyBreakerSelected?.productRule} " +
                        "cableProductFloor=${result.profile.whyCableSelected?.minimumProductSectionMm2} " +
                        "cableProductDefault=${result.profile.whyCableSelected?.productDefaultSectionMm2} " +
                        "cableRule=${result.profile.whyCableSelected?.productRule}"
        )

        return result.profile
    }

    /** FFD-упаковка устройств в группы с лимитом по выбранному автомату. */
    private fun createCircuitGroups(
        devices: List<DeviceEntity>,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        startGroupNumber: Int,
        room: RoomEntity
    ): List<CircuitGroup> {
        val isGeneralSocketBucket = devices.isNotEmpty() && devices.all {
            it.deviceType == DeviceType.SOCKET &&
                !it.requiresSocketConnection &&
                !it.requiresDedicatedCircuit
        }
        val sorted = devices.sortedByDescending { it.installedCurrent() }
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

        val tooBig = sorted.firstOrNull { it.installedCurrent() - limit > eps }
        require(tooBig == null) {
            "Устройство '${tooBig?.name}' в комнате '${room.name}' требует " +
                    "ток ${"%.2f".format(tooBig!!.installedCurrent())} А > лимита группы ${limit} А. Нужна выделенная линия."
        }

        // Розеточные точки одного помещения относятся к одной общей линии.
        // Они не являются отдельными потребителями по 2,2 кВт каждая.
        val bins = if (isGeneralSocketBucket) {
            mutableListOf(sorted.toMutableList())
        } else {
            mutableListOf<MutableList<DeviceEntity>>()
        }
        val sums = if (isGeneralSocketBucket) {
            mutableListOf(sorted.maxOfOrNull { it.installedCurrent() } ?: 0.0)
        } else {
            mutableListOf<Double>()
        }

        if (!isGeneralSocketBucket) {
            for (d in sorted) {
                val cur = d.installedCurrent()
                val idx = sums.indices.firstOrNull { sums[it] + cur <= limit + eps }
                if (idx != null) {
                    bins[idx].add(d)
                    sums[idx] += cur
                } else {
                    bins += mutableListOf(d)
                    sums += cur
                }
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
            // После FFD подбираем характеристику по фактическому составу bin.
            // Мотор в соседней группе не должен менять кривую этой линии.
            val binDomainDevices = bin.map { it.toDomainDevice() }
            val binInstalledCurrent = CircuitLoadCalculator.calculate(binDomainDevices).installedCurrentA
            val binProfile = selectLineProfile(
                nominalCurrent = binInstalledCurrent,
                deviceType = bin.first().deviceType,
                hasMotor = bin.any { it.hasMotor }
            )
            createGroup(
                devices = bin,
                profile = binProfile,
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
        val groupLoad = CircuitLoadCalculator.calculate(devices.map { it.toDomainDevice() })

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
            whyCableSelected = profile.whyCableSelected,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            rcdReasonCodes = safetyProfile.reasons.map { it.name },
            calculationSource = CalculationSource.AUTO,
            algorithmVersion = CalculationAlgorithm.VERSION,
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
            whyCableSelected = profile.whyCableSelected,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            rcdReasonCodes = safetyProfile.reasons.map { it.name },
            calculationSource = CalculationSource.AUTO,
            algorithmVersion = CalculationAlgorithm.VERSION,
            groupNumber = groupNumber,
            installedPowerW = installedPowerW,
            roomId = room.id
        )
    }

    private fun validateBeforeSave(groups: List<CircuitGroup>, mode: PhaseMode) {
        val eps = 1e-6
        groups.forEach { g ->
            requireNotNull(g.phase) { "Группа №${g.groupNumber} без фазы" }
            require(g.nominalCurrent <= g.circuitBreaker + eps) {
                "Группа №${g.groupNumber}: ${"%.2f".format(g.nominalCurrent)} А > ${g.circuitBreaker} А"
            }
            val installedCurrent = CircuitLoadCalculator.calculate(g.devices).installedCurrentA
            require(installedCurrent <= g.circuitBreaker + eps) {
                "Группа №${g.groupNumber}: паспортный ток ${"%.2f".format(installedCurrent)} А > ${g.circuitBreaker} А"
            }
            require(g.devices.isNotEmpty()) { "Группа №${g.groupNumber} не содержит устройств" }
            require(g.devices.all { it.deviceType == g.groupType }) {
                "Группа №${g.groupNumber}: тип группы ${g.groupType} не совпадает с типами устройств"
            }
            require(g.devices.map { it.voltage.type }.distinct().size == 1) {
                "Группа №${g.groupNumber}: нельзя смешивать 1ф и 3ф устройства"
            }
            require(g.devices.map { it.requiresSocketConnection }.distinct().size == 1) {
                "Группа №${g.groupNumber}: нельзя смешивать розеточное и стационарное подключение"
            }
            CircuitCompatibilityPolicy.evaluateGroup(
                CircuitCompatibilityPolicy.GroupInput(
                    phaseMode = mode,
                    phase = g.phase,
                    roomId = g.roomId,
                    devices = g.devices.map { device ->
                        CircuitCompatibilityPolicy.DeviceInput(
                            id = device.id,
                            roomId = requireNotNull(device.roomId) {
                                "Устройство ${device.id} не привязано к помещению"
                            },
                            deviceType = device.deviceType,
                            voltageType = device.voltage.type,
                            requiresDedicatedCircuit = device.requiresDedicatedCircuit,
                            requiresSocketConnection = device.requiresSocketConnection
                        )
                    }
                )
            ).requireAllowed("AUTO группа №${g.groupNumber}")
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

/** Ток для подбора групповой линии без коэффициента спроса. */
fun DeviceEntity.installedCurrent(): Double =
    CurrentCalculator.calculateDeviceLoad(toLoadInput()).installedCurrentA
