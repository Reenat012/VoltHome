package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.GroupProfile
import ru.mugalimov.volthome.domain.model.manual.ManualDeviceDraft
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.policy.breaker.BreakerPolicyInput
import ru.mugalimov.volthome.domain.policy.breaker.BreakerPolicySelector
import ru.mugalimov.volthome.domain.use_case.CalculationTrace
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator
import ru.mugalimov.volthome.domain.use_case.LoadInput

/**
 * Пересчёт параметров линии (номинальный ток / автомат / сечение) при изменении состава группы.
 *
 * ВАЖНО:
 * - НЕ пересобирает группы и НЕ меняет их состав/тип/фазу.
 * - Допускает повышение/понижение номиналов.
 * - Manual path обязан использовать тот же calculation core и тот же breaker policy,
 *   что и AUTO path, иначе UI начнёт расходиться.
 */
class RecalculateGroupLineUseCase @Inject constructor() {

    // ✅ Единый selector policy
    private val breakerPolicySelector = BreakerPolicySelector()

    data class Params(
        val group: ManualGroupDraft,
        val devicesInGroup: List<ManualDeviceDraft>,
    )

    fun execute(p: Params): ManualGroupDraft {
        CalculationTrace.log(
            stage = "MANUAL_LINE_RECALC_START",
            message =
                "groupId=${p.group.groupId} groupNumber=${p.group.groupNumber} " +
                        "devices=${p.devicesInGroup.size} path=RecalculateGroupLineUseCase.execute()"
        )

        if (p.devicesInGroup.isEmpty()) {
            CalculationTrace.log(
                stage = "MANUAL_LINE_RECALC_FINISH",
                message =
                    "groupId=${p.group.groupId} groupNumber=${p.group.groupNumber} " +
                            "result=EMPTY_GROUP nominalCurrent=0 breaker=null cable=null"
            )

            return p.group.copy(
                nominalCurrent = 0.0,
                circuitBreaker = null,
                cableSection = null,
                breakerType = p.group.breakerType,
                whyBreakerSelected = null,
                rcdRequired = p.group.rcdRequired,
                rcdCurrent = p.group.rcdCurrent,
            )
        }

        CalculationTrace.log(
            stage = "MANUAL_LINE_RECALC_INPUT",
            message =
                "groupId=${p.group.groupId} groupNumber=${p.group.groupNumber} " +
                        "devices=" + p.devicesInGroup.joinToString { d ->
                    "id=${d.deviceId},type=${d.deviceType},power=${d.powerW},U=${d.voltageValue},pf=${d.powerFactor},dr=${d.demandRatio}"
                }
        )

        val groupLoad = CurrentCalculator.calculateGroupLoad(
            p.devicesInGroup.map { it.toLoadInput() }
        )

        val nominalI = groupLoad.calculatedCurrentA
        val groupType = p.group.groupType
        val hasMotor = p.devicesInGroup.any { it.hasMotor }

        val line = selectLineProfile(
            nominalCurrent = nominalI,
            hasMotor = hasMotor,
            groupType = groupType
        )

        CalculationTrace.log(
            stage = "MANUAL_LINE_RECALC_CURRENT_PATH",
            message =
                "devices=${p.devicesInGroup.size} manualCurrentA=${CalculationTrace.f(nominalI)} " +
                        "formulaPath=CurrentCalculator.calculateGroupLoad()"
        )

        CalculationTrace.log(
            stage = "MANUAL_LINE_RECALC_FINISH",
            message =
                "groupId=${p.group.groupId} groupNumber=${p.group.groupNumber} " +
                        "manualNominalCurrentA=${CalculationTrace.f(nominalI)} " +
                        "groupType=$groupType hasMotor=$hasMotor " +
                        "selectedBreaker=${line.breakerRating} " +
                        "selectedCable=${CalculationTrace.f(line.cableSection)} " +
                        "selectedCurve=${line.breakerType} " +
                        "reasonFloor=${line.whyBreakerSelected?.floorBreakerA} " +
                        "reasonRequired=${line.whyBreakerSelected?.requiredBreakerA}"
        )

        return p.group.copy(
            nominalCurrent = nominalI,
            circuitBreaker = line.breakerRating,
            cableSection = line.cableSection,
            breakerType = line.breakerType,
            whyBreakerSelected = line.whyBreakerSelected,
        )
    }

    private fun ManualDeviceDraft.toLoadInput(): LoadInput {
        val resolvedVoltage = (voltageValue ?: 0)
            .takeIf { it > 0 }
            ?.toDouble()
            ?: CurrentCalculator.defaultVoltageFor(voltageType)

        return LoadInput(
            powerW = (powerW ?: 0).toDouble(),
            voltage = resolvedVoltage,
            powerFactor = powerFactor,
            demandRatio = demandRatio ?: 1.0,
            voltageType = voltageType,
            label = null
        )
    }

    /**
     * ✅ Manual path теперь использует тот же selector, что и AUTO.
     */
    private fun selectLineProfile(
        nominalCurrent: Double,
        hasMotor: Boolean,
        groupType: ru.mugalimov.volthome.domain.model.DeviceType
    ): GroupProfile {
        return breakerPolicySelector.select(
            BreakerPolicyInput(
                nominalCurrentA = nominalCurrent,
                deviceType = groupType,
                hasMotor = hasMotor
            )
        ).profile
    }
}