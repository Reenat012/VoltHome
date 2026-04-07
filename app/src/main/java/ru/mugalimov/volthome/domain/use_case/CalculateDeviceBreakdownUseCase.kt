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
 * ВАЖНО (Коммит 1):
 * - этот use-case сейчас НЕ является canonical source of truth для device current;
 * - здесь формируется breakdown/объяснение по мощности;
 * - формулы не меняем, только явно помечаем место в системе.
 */
class CalculateDeviceBreakdownUseCase @Inject constructor() {

    fun execute(device: Device): DeviceCalcBreakdown {
        CalculationTrace.log(
            stage = "DEVICE_BREAKDOWN_START",
            message =
                "deviceId=${device.id} name='${device.name}' powerW=${device.power} " +
                        "demandRatio=${device.demandRatio} powerFactor=${device.powerFactor} " +
                        "path=CalculateDeviceBreakdownUseCase.execute()"
        )

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

        CalculationTrace.log(
            stage = "DEVICE_BREAKDOWN_FINISH",
            message =
                "deviceId=${device.id} calculatedPowerW=${CalculationTrace.f(calcPowerW)}"
        )

        return DeviceCalcBreakdown(
            deviceId = device.id,
            calculatedPower = calculatedPower
        )
    }
}