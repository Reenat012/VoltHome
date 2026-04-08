package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
import kotlin.math.ceil
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.manual.ManualDeviceDraft
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.use_case.CalculationTrace
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator
import ru.mugalimov.volthome.domain.use_case.LoadInput

/**
 * Пересчёт параметров линии (номинальный ток / автомат / сечение) при изменении состава группы.
 *
 * ВАЖНО:
 * - НЕ пересобирает группы и НЕ меняет их состав/тип/фазу.
 * - Допускает повышение/понижение номиналов.
 * - Manual path обязан использовать тот же calculation core и ту же product-policy,
 *   что и AUTO path, иначе UI начнёт расходиться по току/автомату/кабелю.
 */
class RecalculateGroupLineUseCase @Inject constructor() {

    data class Params(
        val group: ManualGroupDraft,
        val devicesInGroup: List<ManualDeviceDraft>,
    )

    /**
     * Внутренний профиль линии для manual path.
     *
     * Держим локально в use case, чтобы:
     * - не тащить наружу лишние DTO,
     * - использовать ту же product-логику, что и в AUTO selectBreaker().
     */
    private data class ManualLineProfile(
        val breaker: Int,
        val cableSection: Double,
        val breakerType: String
    )

    fun execute(p: Params): ManualGroupDraft {
        CalculationTrace.log(
            stage = "MANUAL_LINE_RECALC_START",
            message =
                "groupId=${p.group.groupId} groupNumber=${p.group.groupNumber} " +
                        "devices=${p.devicesInGroup.size} path=RecalculateGroupLineUseCase.execute()"
        )

        // Пустая группа — линию не держим.
        // Сама пустая группа дальше должна удаляться каскадом на уровне draft reducer.
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
                rcdRequired = p.group.rcdRequired,
                rcdCurrent = p.group.rcdCurrent,
            )
        }

        // Единый canonical path расчёта нагрузки группы.
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

        // В manual path тип группы берём из самой группы,
        // а моторность считаем по фактическому составу устройств.
        val groupType = p.group.groupType
        val hasMotor = p.devicesInGroup.any { it.hasMotor }

        // Подбор линии делаем по той же product-policy, что и AUTO path,
        // чтобы не было расхождения:
        // - SOCKET -> минимум 16A
        // - LIGHTING -> минимум 10A
        // - heavy/motor loads -> корректная кривая/сечение
        val line = selectLineProfile(
            nominalCurrent = nominalI,
            deviceType = groupType,
            hasMotor = hasMotor
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
                        "selectedBreaker=${line.breaker} " +
                        "selectedCable=${CalculationTrace.f(line.cableSection)} " +
                        "selectedCurve=${line.breakerType}"
        )

        return p.group.copy(
            nominalCurrent = nominalI,
            circuitBreaker = line.breaker,
            cableSection = line.cableSection,
            breakerType = line.breakerType,
        )
    }

    private fun ManualDeviceDraft.toLoadInput(): LoadInput {
        // Manual path обязан использовать фактическое напряжение устройства.
        // Дефолт допустим только как fallback для битого/пустого значения.
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
     * Product-policy подбора линии для manual path.
     *
     * Это сознательно зеркалит текущую логику AUTO selectBreaker():
     * - минимальный номинал зависит от типа группы,
     * - далее берём ближайший допустимый автомат,
     * - кривая D только для реально тяжёлых пусков.
     */
    private fun selectLineProfile(
        nominalCurrent: Double,
        deviceType: DeviceType,
        hasMotor: Boolean
    ): ManualLineProfile {
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

        // Формат:
        // (номинал автомата, сечение кабеля, базовая кривая)
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
            ?: Triple(63, 16.0, "D")

        // D — только для реально больших пусковых нагрузок.
        // Малые моторы оставляем на C, как и в AUTO path.
        val finalCurve = when {
            hasMotor && rating >= 25 -> "D"
            hasMotor -> "C"
            else -> baseCurve
        }

        return ManualLineProfile(
            breaker = rating,
            cableSection = cable,
            breakerType = finalCurve
        )
    }
}