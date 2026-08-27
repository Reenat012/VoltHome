package ru.mugalimov.volthome.domain.use_case.cable

import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.cable.CableCalculationSource
import ru.mugalimov.volthome.domain.model.cable.CableCalculationStatus
import ru.mugalimov.volthome.domain.model.cable.CableCheck
import ru.mugalimov.volthome.domain.model.cable.CableCheckCode
import ru.mugalimov.volthome.domain.model.cable.CableCheckStatus
import ru.mugalimov.volthome.domain.model.cable.CableInsulation
import ru.mugalimov.volthome.domain.model.cable.CableInstallationMethod
import ru.mugalimov.volthome.domain.model.cable.CableLineCalculation
import ru.mugalimov.volthome.domain.model.cable.CableLineInput
import ru.mugalimov.volthome.domain.model.cable.CableSpec
import ru.mugalimov.volthome.domain.model.cable.ConductorMaterial
import ru.mugalimov.volthome.domain.policy.cable.CablePolicyDefaults

/**
 * Детерминированный предварительный инженерный расчёт кабельной линии.
 *
 * Таблица допустимых токов является версионированным встроенным набором данных.
 * Проверка отключения при КЗ и петли повреждения намеренно не имитируется: для неё
 * приложению пока не хватает длины питающей сети и ожидаемого тока КЗ.
 */
class CableLineCalculator @Inject constructor() {

    fun calculate(input: CableLineInput): CableLineCalculation {
        require(input.loadCurrentA >= 0.0) { "Расчётный ток не может быть отрицательным" }
        require(input.breakerA > 0) { "Номинал автомата должен быть положительным" }
        require(input.powerFactor in 0.1..1.0) { "Коэффициент мощности должен быть от 0,1 до 1,0" }

        val length = input.lengthM
        if (length == null || length <= 0.0) return preliminary(input)

        val source = if (input.manualSectionMm2 != null) {
            CableCalculationSource.MANUAL_OVERRIDE
        } else {
            CableCalculationSource.AUTOMATIC
        }
        val section = input.manualSectionMm2 ?: supportedSections.firstOrNull { candidate ->
            evaluate(input, candidate).cableChecksPassed
        } ?: supportedSections.last()

        val evaluated = evaluate(input, section)
        val checks = buildChecks(input, evaluated)
        val status = when {
            checks.any { it.status == CableCheckStatus.FAILED } -> CableCalculationStatus.FAILED
            checks.any { it.status == CableCheckStatus.WARNING } -> CableCalculationStatus.WARNING
            else -> CableCalculationStatus.PASSED
        }

        return CableLineCalculation(
            projectId = input.projectId,
            groupId = input.groupId,
            input = input,
            cable = cableSpec(input.phaseMode, input.defaults.material, input.defaults.insulation, section),
            baseAmpacityA = evaluated.baseAmpacityA,
            installationFactor = evaluated.installationFactor,
            temperatureFactor = evaluated.temperatureFactor,
            groupingFactor = evaluated.groupingFactor,
            correctedAmpacityA = evaluated.correctedAmpacityA,
            voltageDropV = evaluated.voltageDropV,
            voltageDropPercent = evaluated.voltageDropPercent,
            status = status,
            source = source,
            checks = checks
        )
    }

    private fun preliminary(input: CableLineInput): CableLineCalculation {
        val section = input.manualSectionMm2
            ?: input.legacySectionMm2?.takeIf { it > 0.0 }
            ?: CablePolicyDefaults.productDefaultByBreaker(input.breakerA)
        val evaluated = evaluate(input.copy(lengthM = 1.0), section)
        return CableLineCalculation(
            projectId = input.projectId,
            groupId = input.groupId,
            input = input,
            cable = cableSpec(input.phaseMode, input.defaults.material, input.defaults.insulation, section),
            baseAmpacityA = evaluated.baseAmpacityA,
            installationFactor = evaluated.installationFactor,
            temperatureFactor = evaluated.temperatureFactor,
            groupingFactor = evaluated.groupingFactor,
            correctedAmpacityA = evaluated.correctedAmpacityA,
            voltageDropV = null,
            voltageDropPercent = null,
            status = CableCalculationStatus.PRELIMINARY,
            source = CableCalculationSource.LEGACY_PRELIMINARY,
            checks = listOf(
                CableCheck(CableCheckCode.LOAD_VS_BREAKER, if (input.loadCurrentA <= input.breakerA) CableCheckStatus.PASSED else CableCheckStatus.FAILED, "Расчётный ток должен быть не выше номинала автомата"),
                CableCheck(CableCheckCode.BREAKER_VS_AMPACITY, CableCheckStatus.NOT_EVALUATED, "Укажите длину и условия прокладки для проверки допустимого тока"),
                CableCheck(CableCheckCode.VOLTAGE_DROP, CableCheckStatus.NOT_EVALUATED, "Падение напряжения не рассчитано без длины линии"),
                CableCheck(CableCheckCode.SHORT_CIRCUIT, CableCheckStatus.NOT_EVALUATED, "Проверка КЗ требует параметров питающей сети"),
                CableCheck(CableCheckCode.FAULT_LOOP, CableCheckStatus.NOT_EVALUATED, "Петля повреждения пока не рассчитывается")
            )
        )
    }

    private data class Evaluation(
        val baseAmpacityA: Double,
        val installationFactor: Double,
        val temperatureFactor: Double,
        val groupingFactor: Double,
        val correctedAmpacityA: Double,
        val voltageDropV: Double,
        val voltageDropPercent: Double,
        val cableChecksPassed: Boolean
    )

    private fun evaluate(input: CableLineInput, section: Double): Evaluation {
        val materialFactor = if (input.defaults.material == ConductorMaterial.COPPER) 1.0 else 0.78
        val insulationFactor = if (input.defaults.insulation == CableInsulation.XLPE_90) 1.15 else 1.0
        // Пользователь может ввести нестандартное сечение. Для допустимого тока
        // используем ближайшее меньшее табличное сечение, то есть не завышаем Iz.
        // Значение меньше минимального табличного сечения намеренно даёт Iz = 0.
        val tableSection = baseAmpacityCuPvc.keys.filter { it <= section }.maxOrNull()
        val base = (tableSection?.let(baseAmpacityCuPvc::getValue) ?: 0.0) * materialFactor * insulationFactor
        val installation = installationFactor(input.defaults.installationMethod)
        val temperature = temperatureFactor(input.defaults.insulation, input.defaults.ambientTemperatureC)
        val grouping = groupingFactor(input.defaults.groupedCircuits)
        val corrected = base * installation * temperature * grouping
        val drop = voltageDrop(input, section)
        val nominalVoltage = if (input.phaseMode == PhaseMode.THREE) 400.0 else 230.0
        val dropPercent = drop / nominalVoltage * 100.0
        return Evaluation(
            baseAmpacityA = base,
            installationFactor = installation,
            temperatureFactor = temperature,
            groupingFactor = grouping,
            correctedAmpacityA = corrected,
            voltageDropV = drop,
            voltageDropPercent = dropPercent,
            // Сечение выбирается по защите линии и падению напряжения. Если сам
            // автомат меньше расчётного тока, это отдельная ошибка схемы, а не
            // причина ошибочно раздувать кабель до максимального сечения.
            cableChecksPassed = input.breakerA <= corrected &&
                dropPercent <= input.defaults.maxVoltageDropPercent
        )
    }

    private fun buildChecks(input: CableLineInput, e: Evaluation): List<CableCheck> = listOf(
        CableCheck(
            CableCheckCode.LOAD_VS_BREAKER,
            if (input.loadCurrentA <= input.breakerA) CableCheckStatus.PASSED else CableCheckStatus.FAILED,
            "Iрасч ${fmt(input.loadCurrentA)} А ≤ In ${input.breakerA} А"
        ),
        CableCheck(
            CableCheckCode.BREAKER_VS_AMPACITY,
            if (input.breakerA <= e.correctedAmpacityA) CableCheckStatus.PASSED else CableCheckStatus.FAILED,
            "In ${input.breakerA} А ≤ Iz ${fmt(e.correctedAmpacityA)} А после поправочных коэффициентов"
        ),
        CableCheck(
            CableCheckCode.VOLTAGE_DROP,
            if (e.voltageDropPercent <= input.defaults.maxVoltageDropPercent) CableCheckStatus.PASSED else CableCheckStatus.FAILED,
            "ΔU ${fmt(e.voltageDropPercent)}% при пределе ${fmt(input.defaults.maxVoltageDropPercent)}%"
        ),
        CableCheck(CableCheckCode.PROTECTIVE_CONDUCTOR, CableCheckStatus.PASSED, "Сечение PE принято по сечению фазного проводника"),
        CableCheck(CableCheckCode.SHORT_CIRCUIT, CableCheckStatus.NOT_EVALUATED, "Проверка термической стойкости при КЗ требует ожидаемого тока КЗ"),
        CableCheck(CableCheckCode.FAULT_LOOP, CableCheckStatus.NOT_EVALUATED, "Время автоматического отключения требует параметров петли повреждения")
    )

    private fun cableSpec(
        phaseMode: PhaseMode,
        material: ConductorMaterial,
        insulation: CableInsulation,
        section: Double
    ): CableSpec = CableSpec(
        material = material,
        insulation = insulation,
        cores = if (phaseMode == PhaseMode.THREE) 5 else 3,
        phaseSectionMm2 = section,
        neutralSectionMm2 = section,
        protectiveEarthSectionMm2 = protectiveEarthSection(section)
    )

    private fun protectiveEarthSection(section: Double): Double = when {
        section <= 16.0 -> section
        section <= 35.0 -> 16.0
        else -> supportedSections.firstOrNull { it >= ceil(section / 2.0) } ?: 70.0
    }

    private fun voltageDrop(input: CableLineInput, section: Double): Double {
        val lengthKm = (input.lengthM ?: 0.0) / 1000.0
        val alpha = if (input.defaults.material == ConductorMaterial.COPPER) 0.00393 else 0.00403
        val resistivity = if (input.defaults.material == ConductorMaterial.COPPER) 17.5 else 28.2
        val operatingTemperature = input.defaults.insulation.maxOperatingTemperatureC.toDouble()
        val resistance = resistivity / section * (1.0 + alpha * (operatingTemperature - 20.0))
        val reactance = 0.08
        val phi = kotlin.math.acos(input.powerFactor)
        val impedance = resistance * cos(phi) + reactance * sin(phi)
        val multiplier = if (input.phaseMode == PhaseMode.THREE) sqrt(3.0) else 2.0
        return multiplier * lengthKm * input.loadCurrentA * impedance
    }

    private fun installationFactor(method: CableInstallationMethod): Double = when (method) {
        CableInstallationMethod.INSULATED_WALL -> 0.68
        CableInstallationMethod.CONDUIT_WALL -> 0.80
        CableInstallationMethod.CLIPPED_DIRECT -> 1.00
        CableInstallationMethod.TRAY_FREE_AIR -> 1.08
        CableInstallationMethod.BURIED_GROUND -> 0.90
    }

    private fun groupingFactor(count: Int): Double = when (count) {
        1 -> 1.00
        2 -> 0.80
        3 -> 0.70
        4 -> 0.65
        5 -> 0.60
        6 -> 0.57
        7 -> 0.54
        8 -> 0.52
        else -> 0.50
    }

    private fun temperatureFactor(insulation: CableInsulation, temperatureC: Int): Double {
        val table = if (insulation == CableInsulation.PVC_70) pvcTemperatureFactors else xlpeTemperatureFactors
        val minimumTemperature = table.keys.min()
        val maximumTemperature = table.keys.max()

        // Не интерполируем коэффициент вверх и не выбираем математически
        // ближайшую строку: для промежуточной температуры берём следующую
        // большую табличную температуру. Так допустимый ток не завышается.
        // Ниже таблицы используем её нижнюю границу, выше таблицы расчёт
        // намеренно становится непроходимым (коэффициент 0).
        if (temperatureC > maximumTemperature) return 0.0
        val conservativeTemperature = when {
            temperatureC <= minimumTemperature -> minimumTemperature
            else -> table.keys.filter { it >= temperatureC }.min()
        }
        return table.getValue(conservativeTemperature)
    }

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

    companion object {
        private val supportedSections = CablePolicyDefaults.supportedSectionsMm2
        private val baseAmpacityCuPvc = mapOf(
            1.5 to 18.5, 2.5 to 25.0, 4.0 to 34.0, 6.0 to 43.0, 10.0 to 60.0,
            16.0 to 80.0, 25.0 to 101.0, 35.0 to 126.0, 50.0 to 153.0, 70.0 to 196.0
        )
        private val pvcTemperatureFactors = mapOf(10 to 1.22, 20 to 1.12, 25 to 1.06, 30 to 1.00, 35 to 0.94, 40 to 0.87, 45 to 0.79, 50 to 0.71, 55 to 0.61, 60 to 0.50)
        private val xlpeTemperatureFactors = mapOf(10 to 1.15, 20 to 1.08, 25 to 1.04, 30 to 1.00, 35 to 0.96, 40 to 0.91, 45 to 0.87, 50 to 0.82, 55 to 0.76, 60 to 0.71, 65 to 0.65, 70 to 0.58)
    }
}
