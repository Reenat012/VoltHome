package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.*
import javax.inject.Inject

class CalculateDeviceBreakdownUseCase @Inject constructor() {

    fun execute(device: Device): DeviceCalcBreakdown {
        val power = device.power ?: 0
        val demand = device.demandRatio ?: 1.0
        val pf = device.powerFactor ?: 1.0

        val appliedDemand = device.demandRatio ?: 1.0

        val assumptions = buildList {
            if (device.demandRatio == null) {
                add(
                    CalcAssumption(
                        kind = CalcAssumption.Kind.DEFAULT_USED,
                        source = CoefficientSource.DEFAULT,
                        subject = "demandRatio",
                        message = "Коэффициент спроса не задан — использовано значение 1.0",
                        applied = 1.0
                    )
                )
            }
        }

        val calcPowerW: Double = power.toDouble() * appliedDemand

        val calculatedPower = CalculatedValue(
            value = calcPowerW,
            unit = "Вт",
            label = "Расчётная мощность устройства",
            steps = listOf(
                CalcStep(
                    name = "Учет коэффициента спроса",
                    formula = "Pрасч = P × kспроса",
                    inputs = listOf(
                        CalcInput("P", power.toDouble(), "Вт"),
                        CalcInput("kспроса", appliedDemand)
                    ),
                    output = CalcOutput(calcPowerW, "Вт"),
                    assumptions = assumptions
                )
            ),
            assumptions = assumptions
        )

        return DeviceCalcBreakdown(
            deviceId = device.id,
            calculatedPower = calculatedPower
        )
    }
}