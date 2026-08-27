package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DecisionEventType
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Распределяет однофазные группы по фазам A/B/C так, чтобы минимизировать перекос.
 *
 * Алгоритм:
 * 1. Жадная раскладка по canonical current группы.
 * 2. Детерминированная локальная оптимизация тяжёлых групп.
 *
 * Важно:
 * - Вес группы для балансировки = ТОЛЬКО canonical group current.
 * - Никаких псевдослучайных tie-break.
 * - Стабильный порядок фаз: A -> B -> C.
 * - Стабильный порядок групп: по весу убыв., затем по groupNumber, затем по groupId.
 * - 3φ группы (Phase.THREE_PHASE или группы с AC_3PHASE устройствами) НЕ балансируем.
 */
object PhaseDistributor {

    // Ограничение глубины локальной оптимизации.
    private const val TOP_N = 15
    private const val MAX_PASSES = 3

    // Только защита от ошибок сравнения double.
    private const val EPS = 1e-9

    /**
     * Старый API оставляем для совместимости.
     */
    fun distributeGroupsBalanced(input: List<CircuitGroup>): List<CircuitGroup> =
        distributeGroupsBalancedWithLog(input).first

    /**
     * Новый API: возвращает и распределённые группы, и decision log.
     */
    fun distributeGroupsBalancedWithLog(
        input: List<CircuitGroup>
    ): Pair<List<CircuitGroup>, List<DistributionDecision>> {
        if (input.isEmpty()) return emptyList<CircuitGroup>() to emptyList()
        input.forEach { group ->
            require(group.nominalCurrent.isFinite() && group.nominalCurrent >= 0.0) {
                "Группа №${group.groupNumber}: некорректный ток ${group.nominalCurrent} A"
            }
        }

        CalculationTrace.log(
            stage = "PHASE_BALANCER_START",
            message =
                "groups=${input.size} canonicalWeightField=group.nominalCurrent " +
                        "weights=" + input.joinToString { g ->
                    "g#${g.groupNumber}:${CalculationTrace.f(g.nominalCurrent)}A"
                }
        )

        // 3-фазные группы в балансировке не участвуют.
        val threePhaseLike = input
            .filter { g ->
                g.phase == Phase.THREE_PHASE ||
                        g.devices.any { it.voltage.type == VoltageType.AC_3PHASE }
            }
            .map { it.copy(phase = Phase.THREE_PHASE) }

        // В балансировку идут только чистые 1-фазные группы.
        val onePhaseOnly = input.filterNot { g ->
            g.phase == Phase.THREE_PHASE ||
                    g.devices.any { it.voltage.type == VoltageType.AC_3PHASE }
        }

        CalculationTrace.log(
            stage = "PHASE_BALANCER_POOL",
            message =
                "onePhaseOnly=${onePhaseOnly.size} threePhaseLike=${threePhaseLike.size}"
        )

        if (onePhaseOnly.isEmpty()) {
            CalculationTrace.log(
                stage = "PHASE_BALANCER_FINISH",
                message = "result=ONLY_THREE_PHASE groups=${threePhaseLike.size}"
            )
            return threePhaseLike.sortedBy { it.groupNumber } to emptyList()
        }

        val phases = arrayOf(Phase.A, Phase.B, Phase.C)
        val decisions = mutableListOf<DistributionDecision>()

        // Стабильный порядок групп:
        // 1) heavier first
        // 2) lower group number first
        // 3) lower groupId first
        val sorted = onePhaseOnly.sortedWith(
            compareByDescending<CircuitGroup> { it.nominalCurrent }
                .thenBy { it.groupNumber }
                .thenBy { it.groupId }
        )

        CalculationTrace.log(
            stage = "PHASE_BALANCER_SORTED",
            message =
                "order=" + sorted.joinToString { g ->
                    "g#${g.groupNumber}:${CalculationTrace.f(g.nominalCurrent)}A"
                }
        )

        // Нагрузки по фазам A/B/C.
        val loads = doubleArrayOf(0.0, 0.0, 0.0)

        // Назначенные группы по фазам.
        val assigned = arrayOf(
            mutableListOf<CircuitGroup>(),
            mutableListOf<CircuitGroup>(),
            mutableListOf<CircuitGroup>()
        )

        // Жадная раскладка.
        for (g in sorted) {
            val before = loads.toPhaseMap()

            val idx = selectBestPhaseIndexGreedy(
                currentLoads = loads,
                groupCurrent = g.nominalCurrent
            )

            loads[idx] += g.nominalCurrent
            val updated = g.copy(phase = phases[idx])
            assigned[idx] += updated

            val after = loads.toPhaseMap()

            val beforeDelta = currentDelta(
                doubleArrayOf(
                    before[Phase.A] ?: 0.0,
                    before[Phase.B] ?: 0.0,
                    before[Phase.C] ?: 0.0
                )
            )
            val afterDelta = currentDelta(
                doubleArrayOf(
                    after[Phase.A] ?: 0.0,
                    after[Phase.B] ?: 0.0,
                    after[Phase.C] ?: 0.0
                )
            )

            CalculationTrace.log(
                stage = "PHASE_BALANCER_GREEDY_ASSIGN",
                message =
                    "groupNumber=${g.groupNumber} canonicalWeightA=${CalculationTrace.f(g.nominalCurrent)} " +
                            "chosenPhase=${phases[idx]} loadsBefore=$before loadsAfter=$after " +
                            "imbalanceBefore=${CalculationTrace.f(beforeDelta)} " +
                            "imbalanceAfter=${CalculationTrace.f(afterDelta)}"
            )

            decisions += DistributionDecision(
                groupNumber = g.groupNumber,
                groupCurrentA = g.nominalCurrent,
                chosenPhase = phases[idx],
                phaseCurrentsBefore = before,
                phaseCurrentsAfter = after,
                eventType = DecisionEventType.GREEDY_ASSIGN,
                fromPhase = null,
                toPhase = phases[idx],
                imbalanceBeforeA = beforeDelta,
                imbalanceAfterA = afterDelta,
                algorithm = "balanced_greedy_deterministic+local_opt_deterministic",
                tieBreakRule = "MIN_IMBALANCE_THEN_MIN_PHASE_LOAD_THEN_PHASE_ORDER_A_B_C",
                note = "Greedy: picked phase by minimal resulting imbalance, then minimal phase load, then A->B->C."
            )
        }

        // Локальная оптимизация тоже детерминированная.
        val heavy = sorted.take(min(TOP_N, sorted.size))

        for (pass in 0 until MAX_PASSES) {
            var improved = false

            for (g in heavy) {
                val srcIdx = findPhaseIndex(assigned, g.groupNumber)
                if (srcIdx == -1) continue

                val currentDelta = currentDelta(loads)

                var bestIdx = srcIdx
                var bestDelta = currentDelta

                val loadIfRemove = loads[srcIdx] - g.nominalCurrent

                for (dstIdx in 0..2) {
                    if (dstIdx == srcIdx) continue

                    val dstAfter = loads[dstIdx] + g.nominalCurrent

                    val a = when {
                        srcIdx == 0 -> loadIfRemove
                        dstIdx == 0 -> dstAfter
                        else -> loads[0]
                    }
                    val b = when {
                        srcIdx == 1 -> loadIfRemove
                        dstIdx == 1 -> dstAfter
                        else -> loads[1]
                    }
                    val c = when {
                        srcIdx == 2 -> loadIfRemove
                        dstIdx == 2 -> dstAfter
                        else -> loads[2]
                    }

                    val delta = max(a, max(b, c)) - min(a, min(b, c))

                    // Двигаем только при реальном улучшении.
                    if (delta + EPS < bestDelta) {
                        bestDelta = delta
                        bestIdx = dstIdx
                    }
                }

                if (bestIdx != srcIdx) {
                    val before = loads.toPhaseMap()
                    val from = phases[srcIdx]
                    val to = phases[bestIdx]

                    assigned[srcIdx].removeIf { it.groupNumber == g.groupNumber }
                    loads[srcIdx] -= g.nominalCurrent

                    assigned[bestIdx].add(g.copy(phase = to))
                    loads[bestIdx] += g.nominalCurrent

                    val after = loads.toPhaseMap()
                    val beforeDelta = currentDelta(
                        doubleArrayOf(
                            before[Phase.A] ?: 0.0,
                            before[Phase.B] ?: 0.0,
                            before[Phase.C] ?: 0.0
                        )
                    )
                    val afterDelta = currentDelta(
                        doubleArrayOf(
                            after[Phase.A] ?: 0.0,
                            after[Phase.B] ?: 0.0,
                            after[Phase.C] ?: 0.0
                        )
                    )

                    CalculationTrace.log(
                        stage = "PHASE_BALANCER_LOCAL_OPT_MOVE",
                        message =
                            "pass=${pass + 1} groupNumber=${g.groupNumber} " +
                                    "canonicalWeightA=${CalculationTrace.f(g.nominalCurrent)} " +
                                    "from=$from to=$to imbalanceBefore=${CalculationTrace.f(beforeDelta)} " +
                                    "imbalanceAfter=${CalculationTrace.f(afterDelta)}"
                    )

                    decisions += DistributionDecision(
                        groupNumber = g.groupNumber,
                        groupCurrentA = g.nominalCurrent,
                        chosenPhase = to,
                        phaseCurrentsBefore = before,
                        phaseCurrentsAfter = after,
                        eventType = DecisionEventType.LOCAL_OPT_MOVE,
                        fromPhase = from,
                        toPhase = to,
                        imbalanceBeforeA = beforeDelta,
                        imbalanceAfterA = afterDelta,
                        algorithm = "balanced_greedy_deterministic+local_opt_deterministic",
                        tieBreakRule = "STRICT_IMPROVEMENT_ONLY_PHASE_ORDER_A_B_C",
                        note = "LocalOpt(pass=${pass + 1}): moved $from -> $to because delta improved %.3f -> %.3f"
                            .format(currentDelta, bestDelta)
                    )

                    improved = true
                }
            }

            if (!improved) break
        }

        val onePhaseResult = (assigned[0] + assigned[1] + assigned[2])
            .sortedBy { it.groupNumber }

        val finalGroups = (onePhaseResult + threePhaseLike)
            .sortedBy { it.groupNumber }

        CalculationTrace.log(
            stage = "PHASE_BALANCER_FINISH",
            message =
                "finalGroups=${finalGroups.size} decisions=${decisions.size} " +
                        "phases=" + finalGroups.joinToString { g -> "g#${g.groupNumber}:${g.phase}" }
        )

        return finalGroups to decisions
    }

    /**
     * Выбираем лучшую фазу для greedy-назначения.
     *
     * Порядок правил:
     * 1) минимальный resulting imbalance
     * 2) минимальная итоговая нагрузка выбранной фазы
     * 3) порядок фаз A -> B -> C
     */
    private fun selectBestPhaseIndexGreedy(
        currentLoads: DoubleArray,
        groupCurrent: Double
    ): Int {
        var bestIdx = 0
        var bestDelta = Double.POSITIVE_INFINITY
        var bestPhaseLoadAfter = Double.POSITIVE_INFINITY

        for (idx in 0..2) {
            val a = if (idx == 0) currentLoads[0] + groupCurrent else currentLoads[0]
            val b = if (idx == 1) currentLoads[1] + groupCurrent else currentLoads[1]
            val c = if (idx == 2) currentLoads[2] + groupCurrent else currentLoads[2]

            val delta = max(a, max(b, c)) - min(a, min(b, c))
            val phaseLoadAfter = if (idx == 0) a else if (idx == 1) b else c

            val isBetterDelta = delta + EPS < bestDelta
            val isSameDelta = abs(delta - bestDelta) <= EPS
            val isBetterPhaseLoad = phaseLoadAfter + EPS < bestPhaseLoadAfter

            if (isBetterDelta || (isSameDelta && isBetterPhaseLoad)) {
                bestIdx = idx
                bestDelta = delta
                bestPhaseLoadAfter = phaseLoadAfter
            }
        }

        return bestIdx
    }

    private fun currentDelta(loads: DoubleArray): Double {
        val mx = max(loads[0], max(loads[1], loads[2]))
        val mn = min(loads[0], min(loads[1], loads[2]))
        return mx - mn
    }

    private fun findPhaseIndex(
        buckets: Array<MutableList<CircuitGroup>>,
        groupNumber: Int
    ): Int {
        for (i in buckets.indices) {
            if (buckets[i].any { it.groupNumber == groupNumber }) return i
        }
        return -1
    }

    private fun DoubleArray.toPhaseMap(): Map<Phase, Double> = mapOf(
        Phase.A to this[0],
        Phase.B to this[1],
        Phase.C to this[2]
    )
}
