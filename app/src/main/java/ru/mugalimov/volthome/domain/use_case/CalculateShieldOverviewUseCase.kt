package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcInput
import ru.mugalimov.volthome.domain.model.CalcOutput
import ru.mugalimov.volthome.domain.model.CalcStep
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.CoefficientSource

data class ShieldOverviewTotals(
    val installedPowerW: CalculatedValue,
    val calculatedPowerW: CalculatedValue
)

class CalculateShieldOverviewUseCase @Inject constructor() {

    fun execute(groups: List<CircuitGroup>): ShieldOverviewTotals {
        // -----------------------------
        // Installed power (паспортная)
        // -----------------------------
        // Важно: Device.power в домене = Int (non-null)
        val installedPowerW: Int = groups.sumOf { g ->
            g.devices.sumOf { d -> d.power }
        }

        val installedInputs: List<CalcInput> = groups
            .flatMap { it.devices }
            .map { d ->
                val key = "deviceId=${d.id};label=${d.name}"
                CalcInput(
                    name = "$key / base",
                    value = d.power.toDouble(),
                    unit = "Вт"
                )
            }

        val installedSteps: List<CalcStep> = listOf(
            CalcStep(
                name = "Установленная мощность",
                formula = "Pуст(щит) = Σ Pуст,i",
                inputs = installedInputs,
                output = CalcOutput(value = installedPowerW.toDouble(), unit = "Вт"),
                normRefs = emptyList(),
                assumptions = emptyList()
            )
        )

        // -----------------------------
        // Calculated power (с учётом demandRatio)
        // -----------------------------
        // Важно: demandRatio в доменной модели Device = Double (non-null),
        // поэтому ветки "k == null" здесь быть не должно.
        val totalDevices = groups.sumOf { it.devices.size }

        val calculatedPowerW: Int = groups.sumOf { g ->
            g.devices.sumOf { d ->
                val p = d.power
                val k = d.demandRatio
                (p * k).toInt()
            }
        }

        val calculatedAssumptions: List<CalcAssumption> = buildList {
            if (totalDevices > 0) {
                add(
                    CalcAssumption(
                        kind = CalcAssumption.Kind.OTHER,
                        source = CoefficientSource.USER,
                        subject = "demandRatio",
                        message = "Коэффициент спроса задан для всех устройств и использован в расчёте.",
                        original = null,
                        applied = null
                    )
                )
            }
        }

        val calculatedInputs: List<CalcInput> = groups
            .flatMap { it.devices }
            .flatMap { d ->
                val p = d.power
                val k = d.demandRatio
                val result = (p * k).toInt()

                val key = "deviceId=${d.id};label=${d.name}"

                listOf(
                    CalcInput(
                        name = "$key / base",
                        value = p.toDouble(),
                        unit = "Вт"
                    ),
                    CalcInput(
                        name = "$key / k",
                        value = k,
                        unit = ""
                    ),
                    CalcInput(
                        name = "$key / result",
                        value = result.toDouble(),
                        unit = "Вт"
                    )
                )
            }

        val calculatedSteps: List<CalcStep> = listOf(
            CalcStep(
                name = "Расчётная нагрузка",
                formula = "Pрасч(щит) = Σ (Pуст,i × kспроса,i)",
                inputs = calculatedInputs,
                output = CalcOutput(value = calculatedPowerW.toDouble(), unit = "Вт"),
                assumptions = calculatedAssumptions + CalcAssumption(
                    kind = CalcAssumption.Kind.OTHER,
                    source = CoefficientSource.DEFAULT,
                    subject = "power",
                    message = "Коэффициент мощности не влияет на расчёт мощности, используется при расчёте токов.",
                    original = null,
                    applied = null
                )
            )
        )

        return ShieldOverviewTotals(
            installedPowerW = CalculatedValue(
                value = installedPowerW.toDouble(),
                unit = "Вт",
                label = "Установленная мощность",
                steps = installedSteps,
                assumptions = emptyList(),
                warnings = emptyList(),
                normRefs = emptyList()
            ),
            calculatedPowerW = CalculatedValue(
                value = calculatedPowerW.toDouble(),
                unit = "Вт",
                label = "Расчётная нагрузка",
                steps = calculatedSteps,
                assumptions = calculatedAssumptions,
                warnings = emptyList(),
                normRefs = emptyList()
            )
        )
    }
}