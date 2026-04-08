package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcInput
import ru.mugalimov.volthome.domain.model.CalcOutput
import ru.mugalimov.volthome.domain.model.CalcStep
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.CoefficientSource
import ru.mugalimov.volthome.domain.model.Device

/**
 * Breakdown use-case для уже собранной группы.
 *
 * Коммит 2:
 * - installed/calculated power и calculated current теперь идут
 *   через единый canonical calculation core.
 */
class CalculateGroupBreakdownUseCase @Inject constructor() {

    data class Result(
        val installedPower: CalculatedValue,
        val calculatedPower: CalculatedValue,
        val calculatedCurrent: CalculatedValue
    )

    fun execute(group: CircuitGroup): Result {
        CalculationTrace.log(
            stage = "GROUP_BREAKDOWN_START",
            message =
                "groupNumber=${group.groupNumber} groupId=${group.groupId} " +
                        "devices=${group.devices.size} groupNominalCurrentField=${CalculationTrace.f(group.nominalCurrent)} " +
                        "path=CalculateGroupBreakdownUseCase.execute()"
        )

        val inputs = group.devices.map { it.toLoadInput() }
        val groupLoad = CurrentCalculator.calculateGroupLoad(inputs)

        // 1) Installed power
        val installedPW = groupLoad.installedPowerW

        val installedPowerSteps = listOf(
            CalcStep(
                name = "Сумма паспортных мощностей группы",
                formula = "Pгр(уст) = Σ Pуст,i",
                inputs = group.devices.map { d ->
                    CalcInput(
                        name = d.name,
                        value = d.power.toDouble(),
                        unit = "Вт"
                    )
                },
                output = CalcOutput(installedPW, "Вт")
            )
        )

        val installedPower = CalculatedValue(
            value = installedPW,
            unit = "Вт",
            label = "Установленная мощность группы",
            steps = installedPowerSteps
        )

        // 2) Calculated power
        val calculatedPowerInputs = buildList {
            group.devices.forEach { d ->
                val deviceLoad = CurrentCalculator.calculateDeviceLoad(d.toLoadInput())

                add(CalcInput(name = "${d.name} / base", value = d.power.toDouble(), unit = "Вт"))
                add(CalcInput(name = "${d.name} / k", value = d.demandRatio, unit = ""))
                add(CalcInput(name = "${d.name} / result", value = deviceLoad.calculatedPowerW, unit = "Вт"))
            }
        }

        val calculatedPowerSteps = listOf(
            CalcStep(
                name = "Расчётная мощность группы по спросу",
                formula = "Pгр(расч) = Σ (Pуст,i × kспроса,i)",
                inputs = calculatedPowerInputs,
                output = CalcOutput(groupLoad.calculatedPowerW, "Вт")
            )
        )

        val calculatedPower = CalculatedValue(
            value = groupLoad.calculatedPowerW,
            unit = "Вт",
            label = "Расчётная мощность группы",
            steps = calculatedPowerSteps
        )

        // 3) Calculated current
        val calculatedCurrentInputs = buildList {
            group.devices.forEach { d ->
                val baseInstalledCurrent = CurrentCalculator.calculateInstalledCurrent(
                    power = d.power.toDouble(),
                    voltage = d.voltage.value.toDouble(),
                    powerFactor = d.powerFactor,
                    voltageType = d.voltage.type
                )
                val deviceLoad = CurrentCalculator.calculateDeviceLoad(d.toLoadInput())

                add(CalcInput(name = "${d.name} / base", value = baseInstalledCurrent, unit = "А"))
                add(CalcInput(name = "${d.name} / k", value = d.demandRatio, unit = ""))
                add(CalcInput(name = "${d.name} / result", value = deviceLoad.calculatedCurrentA, unit = "А"))
            }
        }

        val currentSteps = listOf(
            CalcStep(
                name = "Сумма расчётных токов устройств с учётом спроса",
                formula = "Iгр(расч) = Σ Iрасч,i",
                inputs = calculatedCurrentInputs,
                output = CalcOutput(groupLoad.calculatedCurrentA, "А"),
                assumptions = listOf(
                    CalcAssumption(
                        kind = CalcAssumption.Kind.OTHER,
                        source = CoefficientSource.DEFAULT,
                        subject = "domainCalc.current",
                        message = "Расчётные токи устройств получены из canonical calculation core.",
                        original = null,
                        applied = null
                    )
                )
            )
        )

        val calculatedCurrent = CalculatedValue(
            value = groupLoad.calculatedCurrentA,
            unit = "А",
            label = "Расчётный ток группы",
            steps = currentSteps
        )

        CalculationTrace.log(
            stage = "GROUP_BREAKDOWN_FINISH",
            message =
                "groupNumber=${group.groupNumber} installedPowerW=${CalculationTrace.f(groupLoad.installedPowerW)} " +
                        "calculatedPowerW=${CalculationTrace.f(groupLoad.calculatedPowerW)} " +
                        "breakdownCurrentA=${CalculationTrace.f(groupLoad.calculatedCurrentA)} " +
                        "groupNominalCurrentField=${CalculationTrace.f(group.nominalCurrent)}"
        )

        return Result(
            installedPower = installedPower,
            calculatedPower = calculatedPower,
            calculatedCurrent = calculatedCurrent
        )
    }

    private fun Device.toLoadInput(): LoadInput =
        LoadInput(
            powerW = power.toDouble(),
            voltage = voltage.value.toDouble(),
            powerFactor = powerFactor,
            demandRatio = demandRatio,
            voltageType = voltage.type,
            label = name
        )
}