package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcInput
import ru.mugalimov.volthome.domain.model.CalcOutput
import ru.mugalimov.volthome.domain.model.CalcStep
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.CoefficientSource

/**
 * Breakdown use-case для уже собранной группы.
 *
 * ВАЖНО (Коммит 1):
 * - этот use-case сейчас НЕ является canonical SoT для group current;
 * - он строит объяснение поверх уже существующей группы;
 * - при этом current здесь считается через Device.calculateCurrent() * demandRatio,
 *   то есть это отдельный parallel path, который мы пока только фиксируем.
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

        // 1) Installed power (ΣPуст) — паспортная
        val installedPW = group.installedPowerW.toDouble()

        val installedPowerSteps = listOf(
            CalcStep(
                name = "Сумма паспортных мощностей группы",
                formula = "Pгр(уст) = Σ Pуст,i",
                inputs = group.devices.map { d ->
                    CalcInput(
                        name = d.name,
                        value = d.power.toDouble(), // power в домене non-null
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

        // 2) Calculated power (Σ(Pуст × kспроса)) — прозрачный шаг по устройствам
        val calculatedPowerInputs = buildList {
            group.devices.forEach { d ->
                val basePW = d.power.toDouble()
                val k = d.demandRatio
                val resultPW = basePW * k

                add(CalcInput(name = "${d.name} / base", value = basePW, unit = "Вт"))
                add(CalcInput(name = "${d.name} / k", value = k, unit = ""))
                add(CalcInput(name = "${d.name} / result", value = resultPW, unit = "Вт"))
            }
        }

        val calculatedPW = group.devices.sumOf { d ->
            val basePW = d.power.toDouble()
            val k = d.demandRatio
            basePW * k
        }

        val calculatedPowerSteps = listOf(
            CalcStep(
                name = "Расчётная мощность группы по спросу",
                formula = "Pгр(расч) = Σ (Pуст,i × kспроса,i)",
                inputs = calculatedPowerInputs,
                output = CalcOutput(calculatedPW, "Вт")
            )
        )

        val calculatedPower = CalculatedValue(
            value = calculatedPW,
            unit = "Вт",
            label = "Расчётная мощность группы",
            steps = calculatedPowerSteps
        )

        // 3) Calculated current (Σ(Iном × kспроса)) — прозрачный шаг по устройствам
        // ВАЖНО:
        // - текущая реализация использует Device.calculateCurrent() как базовый ток,
        //   а потом умножает на demandRatio.
        // - это intentional characterization trace для Коммита 1.
        val calculatedCurrentInputs = buildList {
            group.devices.forEach { d ->
                val baseIA = d.calculateCurrent()
                val k = d.demandRatio
                val resultIA = baseIA * k

                add(CalcInput(name = "${d.name} / base", value = baseIA, unit = "А"))
                add(CalcInput(name = "${d.name} / k", value = k, unit = ""))
                add(CalcInput(name = "${d.name} / result", value = resultIA, unit = "А"))
            }
        }

        val calculatedIA = group.devices.sumOf { d ->
            val baseIA = d.calculateCurrent()
            val k = d.demandRatio
            baseIA * k
        }

        val currentSteps = listOf(
            CalcStep(
                name = "Сумма расчётных токов устройств с учётом спроса",
                formula = "Iгр(расч) = Σ (Iном,i × kспроса,i)",
                inputs = calculatedCurrentInputs,
                output = CalcOutput(calculatedIA, "А"),
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
            value = calculatedIA,
            unit = "А",
            label = "Расчётный ток группы",
            steps = currentSteps
        )

        CalculationTrace.log(
            stage = "GROUP_BREAKDOWN_FINISH",
            message =
                "groupNumber=${group.groupNumber} installedPowerW=${CalculationTrace.f(installedPW)} " +
                        "calculatedPowerW=${CalculationTrace.f(calculatedPW)} " +
                        "breakdownCurrentA=${CalculationTrace.f(calculatedIA)} " +
                        "groupNominalCurrentField=${CalculationTrace.f(group.nominalCurrent)}"
        )

        return Result(
            installedPower = installedPower,
            calculatedPower = calculatedPower,
            calculatedCurrent = calculatedCurrent
        )
    }
}