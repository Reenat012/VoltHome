package ru.mugalimov.volthome.domain.use_case.manual

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.manual.ManualDeviceDraft
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
import ru.mugalimov.volthome.domain.model.manual.toCompatibilityInput
import ru.mugalimov.volthome.domain.policy.breaker.BreakerPolicyDefaults
import ru.mugalimov.volthome.domain.policy.compatibility.CircuitCompatibilityPolicy
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator

/**
 * Pure helpers/selectors for manual draft operations.
 * Keep them deterministic and unit-testable.
 */
object ManualDraftSelectors {

    fun devicesById(draft: ProjectEditState): Map<Long, ManualDeviceDraft> =
        draft.devices.associateBy { it.deviceId }

    fun groupsById(draft: ProjectEditState): Map<Long, ManualGroupDraft> =
        draft.groups.associateBy { it.groupId }

    fun groupContainsDevice(group: ManualGroupDraft, deviceId: Long): Boolean =
        group.deviceIds.contains(deviceId)

    fun findGroupIdContainingDevice(draft: ProjectEditState, deviceId: Long): Long? =
        draft.groups.firstOrNull { it.deviceIds.contains(deviceId) }?.groupId

    fun phaseKeys(mode: PhaseMode): List<Phase> = when (mode) {
        PhaseMode.THREE -> listOf(Phase.A, Phase.B, Phase.C)
        PhaseMode.SINGLE -> listOf(Phase.A)
    }

    fun calcDeviceCurrentA(d: ManualDeviceDraft): Double {
        val voltage = d.voltageValue
            ?.takeIf { it > 0 }
            ?.toDouble()
            ?: CurrentCalculator.defaultVoltageFor(d.voltageType)

        return CurrentCalculator.calculateCalculatedCurrent(
            power = (d.powerW ?: 0).toDouble(),
            voltage = voltage,
            powerFactor = d.powerFactor,
            demandRatio = d.demandRatio ?: 1.0,
            voltageType = d.voltageType
        )
    }

    fun calcDeviceInstalledCurrentA(d: ManualDeviceDraft): Double {
        val voltage = d.voltageValue
            ?.takeIf { it > 0 }
            ?.toDouble()
            ?: CurrentCalculator.defaultVoltageFor(d.voltageType)
        return CurrentCalculator.calculateInstalledCurrent(
            power = (d.powerW ?: 0).toDouble(),
            voltage = voltage,
            powerFactor = d.powerFactor,
            voltageType = d.voltageType
        )
    }

    fun calcGroupInstalledCurrentA(
        group: ManualGroupDraft,
        devices: Map<Long, ManualDeviceDraft>
    ): Double = group.deviceIds.sumOf { id ->
        devices[id]?.let(::calcDeviceInstalledCurrentA) ?: 0.0
    }

    fun calcGroupCurrentA(group: ManualGroupDraft, devices: Map<Long, ManualDeviceDraft>): Double {
        return group.deviceIds.sumOf { id ->
            val d = devices[id] ?: return@sumOf 0.0
            calcDeviceCurrentA(d)
        }
    }

    fun phaseCurrentsA(
        draft: ProjectEditState,
        mode: PhaseMode
    ): Map<Phase, Double> {
        val devices = devicesById(draft)
        val phases = phaseKeys(mode).associateWith { 0.0 }.toMutableMap()

        draft.groups.forEach { g ->
            val ph = g.phase
            if (!phases.containsKey(ph)) return@forEach
            phases[ph] = phases.getValue(ph) + calcGroupCurrentA(g, devices)
        }

        return phases
    }

    fun choosePhaseForNewGroup(
        draft: ProjectEditState,
        mode: PhaseMode
    ): Phase {
        if (mode == PhaseMode.SINGLE) return Phase.A

        val currents = phaseCurrentsA(draft, mode)
        val a = currents[Phase.A] ?: 0.0
        val b = currents[Phase.B] ?: 0.0
        val c = currents[Phase.C] ?: 0.0

        val minVal = min(a, min(b, c))

        return when {
            a == minVal -> Phase.A
            b == minVal -> Phase.B
            else -> Phase.C
        }
    }

    /**
     * Deterministic temp groupId: (min existing groupId <= -1 ? min-1 : -1)
     */
    fun nextTempGroupId(draft: ProjectEditState): Long {
        val minId = draft.groups.minOfOrNull { it.groupId } ?: 0L
        return if (minId <= -1L) minId - 1L else -1L
    }

    fun breakerRatingA(group: ManualGroupDraft): Double {
        // circuitBreaker in your drafts is presumably Int/Double rating in A.
        return (group.circuitBreaker as? Number)?.toDouble()
            ?: group.nominalCurrent
            ?: 0.0
    }

    /**
     * Imbalance metric: max(phase) - min(phase) among active phases.
     * Lower is better.
     */
    fun imbalanceMetric(
        phaseCurrents: Map<Phase, Double>,
        mode: PhaseMode
    ): Double {
        val keys = phaseKeys(mode)
        val values = keys.map { phaseCurrents[it] ?: 0.0 }
        if (values.isEmpty()) return 0.0
        return values.maxOrNull()!! - values.minOrNull()!!
    }

    /**
     * Standard breaker ladder for "upgrade" scenario.
     * Adjust if you have a canonical list elsewhere.
     */
    private val breakerLadder =
        (listOf(6) + BreakerPolicyDefaults.supportedNominalsA).distinct().sorted()

    fun upgradeTargetBreaker(currentBreakerA: Double, neededA: Double): Double? {
        val cur = currentBreakerA.toInt()
        val needed = neededA
        val startIndex = breakerLadder.indexOfFirst { it >= cur }.let { if (it >= 0) it else 0 }
        for (i in startIndex until breakerLadder.size) {
            val cand = breakerLadder[i].toDouble()
            if (cand >= needed) return cand
        }
        return null
    }

    /**
     * Candidate score for placing device into group.
     * Lower tuple is better (lexicographic):
     *  1) capacityViolation (0 fits, 1 needs upgrade, 2 impossible)
     *  2) upgradeDelta (0 if no upgrade, else delta in A)
     *  3) imbalanceAfter (double)
     *  4) groupNumber tie-break (smaller better)
     */
    data class AssignScore(
        val capacityClass: Int,
        val upgradeDelta: Double,
        val imbalanceAfter: Double,
        val groupNumber: Int
    )

    fun scorePlacement(
        draft: ProjectEditState,
        mode: PhaseMode,
        device: ManualDeviceDraft,
        targetGroup: ManualGroupDraft
    ): AssignScore {
        val devices = devicesById(draft)
        val compatibility = CircuitCompatibilityPolicy.evaluatePlacement(
            device = device.toCompatibilityInput(),
            target = CircuitCompatibilityPolicy.GroupInput(
                phaseMode = draft.phaseMode,
                phase = targetGroup.phase,
                roomId = targetGroup.roomId,
                devices = targetGroup.deviceIds.mapNotNull(devices::get)
                    .map { it.toCompatibilityInput() }
            )
        )
        if (!compatibility.allowed) {
            return AssignScore(
                capacityClass = 2,
                upgradeDelta = Double.MAX_VALUE,
                imbalanceAfter = Double.MAX_VALUE,
                groupNumber = targetGroup.groupNumber
            )
        }
        val targetVoltageTypes = targetGroup.deviceIds
            .mapNotNull(devices::get)
            .map { it.voltageType }
            .distinct()
        if (targetVoltageTypes.size > 1 ||
            (targetVoltageTypes.isNotEmpty() && targetVoltageTypes.single() != device.voltageType)
        ) {
            return AssignScore(
                capacityClass = 2,
                upgradeDelta = Double.MAX_VALUE,
                imbalanceAfter = Double.MAX_VALUE,
                groupNumber = targetGroup.groupNumber
            )
        }

        val targetBreaker = breakerRatingA(targetGroup)
        val groupCurrent = calcGroupInstalledCurrentA(targetGroup, devices)
        val installedDeviceCurrent = calcDeviceInstalledCurrentA(device)
        val calculatedDeviceCurrent = calcDeviceCurrentA(device)
        val after = groupCurrent + installedDeviceCurrent

        val (capClass, upgradeDelta) = when {
            after <= targetBreaker && targetBreaker > 0.0 -> 0 to 0.0
            targetBreaker <= 0.0 -> 1 to 0.0 // unknown breaker -> treat as "upgrade needed" bucket but no delta
            else -> {
                val upgraded = upgradeTargetBreaker(targetBreaker, after)
                if (upgraded == null) {
                    2 to Double.MAX_VALUE // impossible
                } else {
                    1 to (upgraded - targetBreaker)
                }
            }
        }

        // compute imbalance after hypothetical placement (phase currents change only for target phase)
        val currents = phaseCurrentsA(draft, mode).toMutableMap()
        val ph = targetGroup.phase
        currents[ph] = (currents[ph] ?: 0.0) + calculatedDeviceCurrent

        val imbalanceAfter = imbalanceMetric(currents, mode)

        return AssignScore(
            capacityClass = capClass,
            upgradeDelta = upgradeDelta,
            imbalanceAfter = imbalanceAfter,
            groupNumber = targetGroup.groupNumber
        )
    }

    fun compareScore(a: AssignScore, b: AssignScore): Int {
        // lexicographic
        if (a.capacityClass != b.capacityClass) return a.capacityClass.compareTo(b.capacityClass)
        if (a.upgradeDelta != b.upgradeDelta) return a.upgradeDelta.compareTo(b.upgradeDelta)
        if (a.imbalanceAfter != b.imbalanceAfter) return a.imbalanceAfter.compareTo(b.imbalanceAfter)
        return a.groupNumber.compareTo(b.groupNumber)
    }
}
