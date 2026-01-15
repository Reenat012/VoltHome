package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcInput
import ru.mugalimov.volthome.domain.model.CalcOutput
import ru.mugalimov.volthome.domain.model.CalcStep
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.CoefficientSource
import javax.inject.Inject

data class ShieldOverviewTotals(
    val installedPowerW: CalculatedValue,
    val calculatedPowerW: CalculatedValue
)

class CalculateShieldOverviewUseCase @Inject constructor() {

    fun execute(groups: List<CircuitGroup>): ShieldOverviewTotals {
        // -----------------------------
        // Installed power (паспортная)
        // -----------------------------
        val installedPowerW: Int = groups.sumOf { g ->
            g.devices.sumOf { d -> (d.power ?: 0) }
        }

        val installedSteps: List<CalcStep> = listOf(
            CalcStep(
                name = "Установленная мощность",
                formula = "Pуст = Σ Pпаспорт",
                inputs = listOf(
                    CalcInput(name = "Σ Pпаспорт", value = installedPowerW.toDouble(), unit = "Вт")
                ),
                output = CalcOutput(value = installedPowerW.toDouble(), unit = "Вт"),
                normRefs = emptyList(),
                assumptions = emptyList()
            )
        )

        // -----------------------------
        // Calculated power (с учётом demandRatio)
        // -----------------------------
        var defaultDemandRatioCount = 0
        var userDemandRatioCount = 0

        val calculatedPowerW: Int = groups.sumOf { g ->
            g.devices.sumOf { d ->
                val p = (d.power ?: 0)
                val k = d.demandRatio
                val applied = if (k == null) {
                    defaultDemandRatioCount++
                    1.0
                } else {
                    userDemandRatioCount++
                    k
                }
                (p * applied).toInt()
            }
        }

        val calculatedAssumptions: List<CalcAssumption> = buildList {
            if (defaultDemandRatioCount > 0) {
                add(
                    CalcAssumption(
                        kind = CalcAssumption.Kind.DEFAULT_USED,
                        source = CoefficientSource.DEFAULT,
                        subject = "demandRatio",
                        message = "Коэффициент спроса не задан для $defaultDemandRatioCount устройств — применено значение по умолчанию.",
                        original = null,
                        applied = 1.0
                    )
                )
            }
            if (userDemandRatioCount > 0) {
                add(
                    CalcAssumption(
                        kind = CalcAssumption.Kind.OTHER,
                        source = CoefficientSource.USER,
                        subject = "demandRatio",
                        message = "Коэффициент спроса задан вручную для $userDemandRatioCount устройств — использован в расчёте.",
                        original = null,
                        applied = null
                    )
                )
            }
        }

        val calculatedSteps: List<CalcStep> = listOf(
            CalcStep(
                name = "Расчётная нагрузка",
                formula = "Pрасч = Σ (Pпаспорт × kспроса)",
                inputs = listOf(
                    CalcInput(name = "Σ(Pпаспорт×k)", value = calculatedPowerW.toDouble(), unit = "Вт"),
                    CalcInput(name = "k по умолч.", value = defaultDemandRatioCount.toDouble(), unit = "шт"),
                    CalcInput(name = "k задано", value = userDemandRatioCount.toDouble(), unit = "шт"),
                ),
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