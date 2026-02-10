package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcInput
import ru.mugalimov.volthome.domain.model.CalcOutput
import ru.mugalimov.volthome.domain.model.CalcStep
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.CoefficientSource
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceCalcBreakdown

class CalculateDeviceBreakdownUseCase @Inject constructor() {

    fun execute(device: Device): DeviceCalcBreakdown {
        // Важно: в домене power/demandRatio/powerFactor — non-null
        val powerW = device.power
        val appliedDemand = device.demandRatio

        // В этом проекте demandRatio в доменной модели non-null,
        // значит допущение "не задан" тут больше не актуально.
        val assumptions = emptyList<CalcAssumption>()

        val calcPowerW: Double = powerW.toDouble() * appliedDemand

        val calculatedPower = CalculatedValue(
            value = calcPowerW,
            unit = "Вт",
            label = "Расчётная мощность устройства",
            steps = listOf(
                CalcStep(
                    name = "Учет коэффициента спроса",
                    formula = "Pрасч = Pуст × kспроса",
                    inputs = listOf(
                        CalcInput("Pуст", powerW.toDouble(), "Вт"),
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