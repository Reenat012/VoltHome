import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseImbalance
import ru.mugalimov.volthome.domain.model.VoltageType

/**
 * Жадное распределение по фазам + небольшая локальная оптимизация первых topN.
 * ВАЖНО: номер группы (groupNumber) НЕ меняем — только phase.
 */
fun distributeGroupsBalanced(
    groups: List<CircuitGroup>,
    topN: Int = 10,
    maxPasses: Int = 2
): List<CircuitGroup> {
    if (groups.isEmpty()) return emptyList()

    val phases = listOf(Phase.A, Phase.B, Phase.C)

    // детерминированно: по току ↓, при равенстве — по номеру ↑
    val sorted = groups.sortedWith(
        compareByDescending<CircuitGroup> { it.nominalCurrent }
            .thenBy { it.groupNumber }
    )

    // 1) Жадная раскладка
    val loads = doubleArrayOf(0.0, 0.0, 0.0)
    val phaseOf = IntArray(sorted.size) { -1 }
    sorted.forEachIndexed { idx, g ->
        val pi = loads.indices.minBy { loads[it] }
        loads[pi] += g.nominalCurrent
        phaseOf[idx] = pi
    }

    // 2) Локальная оптимизация для первых topN тяжёлых
    val K = minOf(topN, sorted.size)
    repeat(maxPasses.coerceAtLeast(0)) {
        var improved = false
        for (i in 0 until K) {
            val g = sorted[i]
            val curPi = phaseOf[i]
            var bestPi = curPi
            var bestImb = imbalance(loads)

            for (pi in 0..2) if (pi != curPi) {
                val test = loads.copyOf()
                test[curPi] -= g.nominalCurrent
                test[pi]    += g.nominalCurrent
                val imb = imbalance(test)
                if (imb + 1e-9 < bestImb) { bestImb = imb; bestPi = pi }
            }

            if (bestPi != curPi) {
                loads[curPi] -= g.nominalCurrent
                loads[bestPi] += g.nominalCurrent
                phaseOf[i] = bestPi
                improved = true
            }
        }
        if (!improved) return@repeat
    }

    // Формируем результат: НЕ трогаем номера групп
    val assigned = sorted.mapIndexed { idx, g ->
        g.copy(phase = phases[phaseOf[idx]])
    }
    return assigned.sortedBy { it.groupNumber }
}

/** Перекос по фазам = max(load) - min(load) */
private fun imbalance(loads: DoubleArray): Double {
    var mn = Double.POSITIVE_INFINITY
    var mx = Double.NEGATIVE_INFINITY
    for (x in loads) { if (x < mn) mn = x; if (x > mx) mx = x }
    return mx - mn
}

/** Суммирует токи по уже назначенным фазам (безопасно к null) */
fun phaseCurrents(groups: List<CircuitGroup>): Map<Phase, Double> =
    groups.filter { it.phase != null }
        .groupBy { it.phase!! }
        .mapValues { (_, gs) -> gs.sumOf { it.nominalCurrent } }
        .withDefault { 0.0 }

/** 3Ф, если задействовано ≥ 2 фаз; иначе 1Ф */
fun inferVoltageType(groups: List<CircuitGroup>): VoltageType {
    val used = groups.mapNotNull { it.phase }.toSet()
    return if (used.size >= 2) VoltageType.AC_3PHASE else VoltageType.AC_1PHASE
}

/** Удобный геттер (если где-то нужна карта с нулями) */
fun Map<Phase, Double>.getOrZero(phase: Phase): Double = getOrDefault(phase, 0.0)

/** Компоновка метрик перекоса (для UI) */
fun computeImbalance(perPhase: Map<Phase, Double>): PhaseImbalance {
    val a = perPhase.getOrDefault(Phase.A, 0.0)
    val b = perPhase.getOrDefault(Phase.B, 0.0)
    val c = perPhase.getOrDefault(Phase.C, 0.0)
    val iMax = maxOf(a, b, c)
    val iMin = minOf(a, b, c)
    val iAvg = (a + b + c) / 3.0
    val delta = iMax - iMin
    val pct = if (iAvg > 0) (delta / iAvg) * 100.0 else 0.0
    val worst = when (iMax) { a -> Phase.A; b -> Phase.B; else -> Phase.C }
    return PhaseImbalance(a, b, c, iMax, iMin, iAvg, delta, pct, worst)
}