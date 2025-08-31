package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType

/** Считает суммарный ток по каждой фазе из уже распределённых групп */
fun phaseCurrents(groups: List<CircuitGroup>): Map<Phase, Double> =
    groups.groupBy { it.phase }
        .mapValues { (_, gs) -> gs.sumOf { it.nominalCurrent } }
        .withDefault { 0.0 }

/** Определяет тип сети на основании фактического распределения фаз */
fun inferVoltageType(groups: List<CircuitGroup>): VoltageType {
    val distinct = groups.map { it.phase }.toSet()
    return if (distinct.size >= 2) VoltageType.AC_3PHASE else VoltageType.AC_1PHASE
}

/** Безопасный доступ с 0.0 по умолчанию */
fun Map<Phase, Double>.getOrZero(phase: Phase): Double = this.getOrDefault(phase, 0.0)
