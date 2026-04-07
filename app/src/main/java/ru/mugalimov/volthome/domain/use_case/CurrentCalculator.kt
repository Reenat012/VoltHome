package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.VoltageType

/**
 * CANONICAL FORMULA ENTRY POINT (Коммит 1).
 *
 * На текущем этапе проекта именно этот объект считается
 * главным кандидатом на canonical path для расчёта nominal/calculated current,
 * потому что здесь одновременно учитываются:
 * - power
 * - demandRatio
 * - powerFactor
 * - voltage / voltageType
 *
 * ВАЖНО:
 * - мы НЕ меняем формулу в этом коммите;
 * - мы только фиксируем, что дальнейшая унификация должна идти относительно
 *   этого пути, а не относительно Device.calculateCurrent() или UI-агрегатов.
 */
object CurrentCalculator {

    fun calculateNominalCurrent(
        power: Double,
        voltage: Double,
        powerFactor: Double?,
        demandRatio: Double,
        voltageType: VoltageType
    ): Double {
        val pf = (powerFactor ?: 1.0).coerceAtLeast(0.2) // защита от нуля/мусора
        val volt = if (voltage > 0) voltage else 230.0

        return when (voltageType) {
            VoltageType.AC_1PHASE -> (power * demandRatio) / (volt * pf)
            VoltageType.AC_3PHASE -> (power * demandRatio) / (1.732 * volt * pf)
            VoltageType.DC -> (power * demandRatio) / volt
        }
    }
}