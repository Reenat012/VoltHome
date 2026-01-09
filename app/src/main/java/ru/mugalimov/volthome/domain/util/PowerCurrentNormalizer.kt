package ru.mugalimov.volthome.domain.util


import android.util.Log
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import kotlin.math.roundToInt
import kotlin.math.sqrt
import ru.mugalimov.volthome.domain.model.CalcAssumption

/**
+ * Заполняет недостающее поле мощности (Вт) или тока (А) по известному второму.
+ * Единицы: строго Вт/А. Никаких кВт.
+ */
object PowerCurrentNormalizer {

    private const val TAG = "PowerCurrentNormalizer"
    private val SQRT3 = sqrt(3.0)

    data class NormalizedPowerCurrent(
        val powerW: Int?,
        val currentA: Double?,
        val assumptions: List<CalcAssumption> = emptyList()
    )

    /**
     * @param powerW   мощность в Вт (может быть null/0)
     * @param currentA ток в А (может быть null/0.0)
     * @param voltage  модель напряжения (значение и тип сети)
     * @param powerFactor коэффициент мощности; если пришёл мусор — будет зажат в (0,1]
     *
     * @return NormalizedPowerCurrent (powerW, currentA, assumptions)
     */
    fun ensurePAndI(
        powerW: Int?,
        currentA: Double?,
        voltage: Voltage,
        powerFactor: Double?
    ): NormalizedPowerCurrent {
        val (pf, pfAssumption) = sanitizePfWithAssumption(powerFactor)
        val assumptions = buildList {
            pfAssumption?.let { add(it) }
        }
        var p = powerW?.takeIf { it > 0 }
        var i = currentA?.takeIf { it > 0.0 }

        when {
            p != null && i == null -> {
                i = when (voltage.type) {
                    VoltageType.AC_3PHASE -> p.toDouble() / (SQRT3 * voltage.value * pf)
                    else -> p.toDouble() / (voltage.value * pf)
                }
            }

            i != null && p == null -> {
                val calc = when (voltage.type) {
                    VoltageType.AC_3PHASE -> i * SQRT3 * voltage.value * pf
                    else -> i * voltage.value * pf
                }
                p = calc.roundToInt()
            }
        }

        if ((p ?: 0) == 0 && (i ?: 0.0) > 0.0) {
            Log.w(
                TAG,
                "Inconsistent device numbers: power=0W while current=${i}A (voltage=${voltage.value}, pf=$pf)"
            )
        }
        if ((p ?: 0) > 100000 || (i ?: 0.0) > 1000) {
            Log.w(TAG, "Suspicious device numbers: power=${p}W, current=${i}A")
        }

        return NormalizedPowerCurrent(
            powerW = p,
            currentA = i,
            assumptions = assumptions
        )
    }

    private fun sanitizePfWithAssumption(pf: Double?): Pair<Double, CalcAssumption?> {
        val v = pf
        val applied = 1.0
        return when {
            v == null -> {
                applied to CalcAssumption(
                    kind = CalcAssumption.Kind.DEFAULT_USED,
                    subject = "powerFactor",
                    message = "Коэффициент мощности не задан — применено значение по умолчанию.",
                    original = null,
                    applied = applied
                )
            }

            v.isNaN() || v.isInfinite() -> {
                applied to CalcAssumption(
                    kind = CalcAssumption.Kind.NORMALIZED,
                    subject = "powerFactor",
                    message = "Коэффициент мощности некорректен (NaN/Inf) — применено значение по умолчанию.",
                    original = v,
                    applied = applied
                )
            }

            v <= 0.0 -> {
                applied to CalcAssumption(
                    kind = CalcAssumption.Kind.NORMALIZED,
                    subject = "powerFactor",
                    message = "Коэффициент мощности <= 0 — применено значение по умолчанию.",
                    original = v,
                    applied = applied
                )
            }

            v > 1.0 -> {
                applied to CalcAssumption(
                    kind = CalcAssumption.Kind.NORMALIZED,
                    subject = "powerFactor",
                    message = "Коэффициент мощности > 1 — применено значение по умолчанию.",
                    original = v,
                    applied = applied
                )
            }

            else -> v to null
        }
    }
}