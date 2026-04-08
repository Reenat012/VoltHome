package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcInput
import ru.mugalimov.volthome.domain.model.CalcOutput
import ru.mugalimov.volthome.domain.model.CalcStep
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceCalcBreakdown

/**
 * Device breakdown use-case.
 *
 * Коммит 2:
 * - breakdown мощности устройства теперь идёт через canonical calculation core,
 *   а не через локальную формулу use-case.
 */
class CalculateDeviceBreakdownUseCase @Inject constructor() {

    fun execute(device: Device): DeviceCalcBreakdown {
        CalculationTrace.log(
            stage = "DEVICE_BREAKDOWN_START",
            message =
                "deviceId=${device.id} name='${device.name}' powerW=${device.power} " +
                        "voltage=${device.voltage.value} voltageType=${device.voltage.type} " +
                        "demandRatio=${device.demandRatio} powerFactor=${device.powerFactor} " +
                        "path=CalculateDeviceBreakdownUseCase.execute()"
        )

        val input = LoadInput(
            powerW = device.power.toDouble(),
            voltage = device.voltage.value.toDouble(),
            powerFactor = device.powerFactor,
            demandRatio = device.demandRatio,
            voltageType = device.voltage.type,
            label = device.name
        )

        val deviceLoad = CurrentCalculator.calculateDeviceLoad(input)

        val assumptions = emptyList<CalcAssumption>()

        val calculatedPower = CalculatedValue(
            value = deviceLoad.calculatedPowerW,
            unit = "Вт",
            label = "Расчётная мощность устройства",
            steps = listOf(
                CalcStep(
                    name = "Учет коэффициента спроса",
                    formula = "Pрасч = Pуст × kспроса",
                    inputs = listOf(
                        CalcInput("Pуст", device.power.toDouble(), "Вт"),
                        CalcInput("kспроса", device.demandRatio)
                    ),
                    output = CalcOutput(deviceLoad.calculatedPowerW, "Вт"),
                    assumptions = assumptions
                )
            ),
            assumptions = assumptions
        )

        CalculationTrace.log(
            stage = "DEVICE_BREAKDOWN_FINISH",
            message =
                "deviceId=${device.id} calculatedPowerW=${CalculationTrace.f(deviceLoad.calculatedPowerW)} " +
                        "calculatedCurrentA=${CalculationTrace.f(deviceLoad.calculatedCurrentA)}"
        )

        return DeviceCalcBreakdown(
            deviceId = device.id,
            calculatedPower = calculatedPower
        )
    }
}