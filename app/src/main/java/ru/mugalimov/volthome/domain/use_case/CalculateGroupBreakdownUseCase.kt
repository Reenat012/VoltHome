package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.*
import javax.inject.Inject

class CalculateGroupBreakdownUseCase @Inject constructor() {

    data class Result(
        val installedPower: CalculatedValue,
        val calculatedCurrent: CalculatedValue
    )

    fun execute(group: CircuitGroup): Result {
        // 1) Installed power (ΣP)
        val pW = group.installedPowerW.toDouble()

        val powerSteps = listOf(
            CalcStep(
                name = "Сумма паспортных мощностей группы",
                formula = "Pгр = Σ Pi",
                inputs = group.devices.map { d ->
                    CalcInput(
                        name = d.name,
                        value = (d.power ?: 0).toDouble(),
                        unit = "Вт"
                    )
                },
                output = CalcOutput(pW, "Вт")
            )
        )

        val installedPower = CalculatedValue(
            value = pW,
            unit = "Вт",
            label = "Установленная мощность группы",
            steps = powerSteps
        )

        // 2) Calculated current (ΣI) — используем то же, что уже доменно считается
        // group.nominalCurrent уже учитывает demandRatio/pf (через nominalCurrent() в калькуляторе)
        val iA = group.nominalCurrent

        val currentSteps = listOf(
            CalcStep(
                name = "Сумма расчётных токов устройств",
                formula = "Iгр = Σ Iрасч,i",
                inputs = group.devices.map { d ->
                    CalcInput(
                        name = d.name,
                        value = d.calculateCurrent(),
                        unit = "А"
                    )
                },
                output = CalcOutput(iA, "А"),
                assumptions = listOf(
                    CalcAssumption(
                        kind = CalcAssumption.Kind.OTHER,
                        source = CoefficientSource.DEFAULT,
                        subject = "domainCalc.current",
                        message = "Токи устройств рассчитаны с учётом коэффициента спроса и коэффициента мощности.",
                        original = null,
                        applied = null
                    )
                )
            )
        )

        val calculatedCurrent = CalculatedValue(
            value = iA,
            unit = "А",
            label = "Расчётный ток группы",
            steps = currentSteps
        )

        return Result(
            installedPower = installedPower,
            calculatedCurrent = calculatedCurrent
        )
    }
}