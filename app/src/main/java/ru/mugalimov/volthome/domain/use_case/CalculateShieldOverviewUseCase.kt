package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CoefficientSource
import ru.mugalimov.volthome.domain.model.CircuitGroup
import javax.inject.Inject

data class ShieldOverviewTotals(
    val installedPowerW: Int,
    val calculatedPowerW: Int,
    val assumptions: List<CalcAssumption> = emptyList()
)

class CalculateShieldOverviewUseCase @Inject constructor() {

    fun execute(groups: List<CircuitGroup>): ShieldOverviewTotals {
        val installedPowerW = groups.sumOf { g ->
            g.devices.sumOf { d -> (d.power ?: 0) }
        }

        var defaultDemandRatioCount = 0
        var userDemandRatioCount = 0

        val calculatedPowerW = groups.sumOf { g ->
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

        val assumptions = buildList {
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

            // ВАЖНО: это опционально. Можно не добавлять, чтобы не шуметь.
            // Но если тебе важно видеть, что пользовательские коэффициенты реально участвовали:
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

        return ShieldOverviewTotals(
            installedPowerW = installedPowerW,
            calculatedPowerW = calculatedPowerW,
            assumptions = assumptions
        )
    }
}