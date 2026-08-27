package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.GroupProfile
import ru.mugalimov.volthome.domain.model.manual.ManualDeviceDraft
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.policy.line.LinePolicyInput
import ru.mugalimov.volthome.domain.policy.line.LinePolicySelector
import ru.mugalimov.volthome.domain.policy.protection.RcdDeviceInput
import ru.mugalimov.volthome.domain.policy.protection.RcdSelectionPolicy
import ru.mugalimov.volthome.domain.policy.protection.RcdSelectionReason
import ru.mugalimov.volthome.domain.policy.protection.RcdSpecFactory
import ru.mugalimov.volthome.domain.use_case.CalculationTrace
import ru.mugalimov.volthome.domain.use_case.CircuitLoadCalculator
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator
import ru.mugalimov.volthome.domain.use_case.LoadInput

/**
 * Пересчёт параметров линии при изменении состава группы.
 *
 * ВАЖНО:
 * - НЕ пересобирает группы и НЕ меняет их состав/тип/фазу;
 * - допускает повышение/понижение номиналов;
 * - manual path обязан использовать тот же calculation core и тот же line policy,
 *   что и AUTO path, иначе UI начнёт расходиться.
 */
class RecalculateGroupLineUseCase @Inject constructor() {

    // ✅ Единый selector линии: breaker -> cable
    private val linePolicySelector = LinePolicySelector()
    private val rcdSelectionPolicy = RcdSelectionPolicy()

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
            val preservedReasons = p.group.rcdReasons
                .filter { it == RcdSelectionReason.SPECIAL_ROOM }
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
                whyCableSelected = null,
                rcdRequired = preservedReasons.isNotEmpty(),
                rcdCurrent = p.group.rcdCurrent,
                rcdReasons = preservedReasons,
                rcdSpec = null,
            )
        }

        // MANUAL не блокирует даже смешанную топологию. CurrentCalculator считает
        // вклад каждого устройства по его собственному типу напряжения, после чего
        // используется консервативная сумма токов. Несовместимость сохраняется в
        // deviationCodes и показывается пользователю как осознанное отклонение.

        CalculationTrace.log(
            stage = "MANUAL_LINE_RECALC_INPUT",
            message =
                "groupId=${p.group.groupId} groupNumber=${p.group.groupNumber} " +
                        "devices=" + p.devicesInGroup.joinToString { d ->
                    "id=${d.deviceId},type=${d.deviceType},power=${d.powerW},U=${d.voltageValue},pf=${d.powerFactor},dr=${d.demandRatio}"
                }
        )

        val groupLoad = CircuitLoadCalculator.calculateEntries(
            p.devicesInGroup.map { device ->
                CircuitLoadCalculator.Entry(
                    roomId = device.roomId,
                    deviceType = device.deviceType,
                    requiresSocketConnection = device.requiresSocketConnection,
                    requiresDedicatedCircuit = device.requiresDedicatedCircuit,
                    loadInput = device.toLoadInput()
                )
            }
        )

        val nominalI = groupLoad.calculatedCurrentA
        val installedI = groupLoad.installedCurrentA
        val groupType = p.group.groupType
        val hasMotor = p.devicesInGroup.any { it.hasMotor }

        val line = selectStrictestLineProfile(
            nominalCurrent = installedI,
            hasMotor = hasMotor,
            deviceTypes = p.devicesInGroup.map { it.deviceType }.toSet()
        )
        val rcdReasons = selectRcdReasons(
            group = p.group,
            devices = p.devicesInGroup
        )

        CalculationTrace.log(
            stage = "MANUAL_LINE_RECALC_CURRENT_PATH",
            message =
                "devices=${p.devicesInGroup.size} manualCurrentA=${CalculationTrace.f(nominalI)} " +
                        "formulaPath=CircuitLoadCalculator.calculate()"
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
                        "reasonRequired=${line.whyBreakerSelected?.requiredBreakerA} " +
                        "cableProductFloor=${line.whyCableSelected?.minimumProductSectionMm2} " +
                        "cableDefault=${line.whyCableSelected?.productDefaultSectionMm2}"
        )

        return p.group.copy(
            nominalCurrent = nominalI,
            circuitBreaker = line.breakerRating,
            cableSection = line.cableSection,
            breakerType = line.breakerType,
            whyBreakerSelected = line.whyBreakerSelected,
            whyCableSelected = line.whyCableSelected,
            rcdRequired = rcdReasons.isNotEmpty(),
            rcdCurrent = if (rcdReasons.isNotEmpty()) 30 else p.group.rcdCurrent,
            rcdReasons = rcdReasons,
            rcdSpec = RcdSpecFactory.createGroupRecommendation(
                required = rcdReasons.isNotEmpty(),
                leakageCurrentMa = if (rcdReasons.isNotEmpty()) 30 else null,
                breakerRatingA = line.breakerRating,
                phase = p.group.phase,
                source = ru.mugalimov.volthome.domain.model.CalculationSource.MANUAL
            ),
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

    private fun selectRcdReasons(
        group: ManualGroupDraft,
        devices: List<ManualDeviceDraft>
    ): List<RcdSelectionReason> {
        val dynamic = rcdSelectionPolicy.select(
            roomType = group.roomType,
            devices = devices.map { device ->
                RcdDeviceInput(
                    deviceType = device.deviceType,
                    requiresSocketConnection = device.requiresSocketConnection
                )
            }
        ).reasons

        return buildList {
            if (RcdSelectionReason.SPECIAL_ROOM in group.rcdReasons) {
                add(RcdSelectionReason.SPECIAL_ROOM)
            }
            addAll(dynamic)
        }.distinct()
    }

    /**
     * ✅ Manual path теперь использует тот же line selector, что и AUTO.
     *
     * Порядок строго такой:
     * 1) breaker
     * 2) cable
     */
    private fun selectLineProfile(
        nominalCurrent: Double,
        hasMotor: Boolean,
        groupType: ru.mugalimov.volthome.domain.model.DeviceType
    ): GroupProfile {
        return linePolicySelector.select(
            LinePolicyInput(
                nominalCurrentA = nominalCurrent,
                deviceType = groupType,
                hasMotor = hasMotor
            )
        ).profile
    }

    /**
     * MANUAL может содержать устройства разных назначений. В таком случае
     * нельзя продолжать выбирать линию по историческому groupType: берём
     * наиболее строгий результат среди фактических типов устройств.
     */
    private fun selectStrictestLineProfile(
        nominalCurrent: Double,
        hasMotor: Boolean,
        deviceTypes: Set<ru.mugalimov.volthome.domain.model.DeviceType>
    ): GroupProfile {
        require(deviceTypes.isNotEmpty()) { "Нельзя выбрать линию для пустого набора типов" }
        return deviceTypes
            .map { type ->
                selectLineProfile(
                    nominalCurrent = nominalCurrent,
                    hasMotor = hasMotor,
                    groupType = type
                )
            }
            .maxWith(
                compareBy<GroupProfile> { it.breakerRating }
                    .thenBy { it.cableSection }
                    .thenBy { curveRank(it.breakerType) }
            )
    }

    private fun curveRank(curve: String): Int = when (curve.uppercase()) {
        "D" -> 3
        "C" -> 2
        "B" -> 1
        else -> 0
    }
}
