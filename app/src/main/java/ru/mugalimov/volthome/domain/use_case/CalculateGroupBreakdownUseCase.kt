package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.*
import javax.inject.Inject

class CalculateGroupBreakdownUseCase @Inject constructor() {

    data class Result(
        val installedPower: CalculatedValue,
        val calculatedPower: CalculatedValue,
        val calculatedCurrent: CalculatedValue
    )

    fun execute(group: CircuitGroup): Result {
        // 1) Installed power (ΣPуст) — как было: без спроса
        val installedPW = group.installedPowerW.toDouble()

        val installedPowerSteps = listOf(
            CalcStep(
                name = "Сумма паспортных мощностей группы",
                formula = "Pгр(уст) = Σ Pуст,i",
                inputs = group.devices.map { d ->
                    CalcInput(
                        name = d.name,
                        value = (d.power ?: 0).toDouble(),
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
                val basePW = (d.power ?: 0).toDouble()
                val k = d.demandRatio
                val resultPW = basePW * k

                add(CalcInput(name = "${d.name} / base", value = basePW, unit = "Вт"))
                add(CalcInput(name = "${d.name} / k", value = k, unit = ""))
                add(CalcInput(name = "${d.name} / result", value = resultPW, unit = "Вт"))
            }
        }

        val calculatedPW = group.devices.sumOf { d ->
            val basePW = (d.power ?: 0).toDouble()
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
        // base: ток устройства без спроса (через P/U/cosφ)
        // k: demandRatio
        // result: вклад тока устройства с учётом спроса
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

        return Result(
            installedPower = installedPower,
            calculatedPower = calculatedPower,
            calculatedCurrent = calculatedCurrent
        )
    }
}