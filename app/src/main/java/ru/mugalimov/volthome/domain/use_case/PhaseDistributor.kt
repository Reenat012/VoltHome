package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import kotlin.math.max
import kotlin.math.min

/**
 * Распределяет однофазные группы по фазам A/B/C так, чтобы минимизировать перекос.
 * Алгоритм: жадная раскладка + локальная оптимизация topN «тяжёлых» групп.
 *
 * Важно:
 * - Номера групп не меняем.
 * - phase переприсваиваем только в результате.
 * - 3φ группы (Phase.THREE_PHASE или группы с устройствами AC_3PHASE) НЕ балансируем.
 * - Возвращаем decision log (пока без UI) — доказательная база “почему так”.
 */
object PhaseDistributor {

    // ⬇ чуть глубже локальная оптимизация
    private const val TOP_N = 15
    private const val MAX_PASSES = 3
    private const val EPS = 1e-9

    /**
     * Крошечный детерминированный «шум» для устойчивых тай-брейков при равных нагрузках.
     * Не влияет на итоговые суммы заметно (±0.005 A), но убирает прилипание к фазе A.
     */
    private fun weightWithEpsilon(value: Double, seed: Int): Double {
        // Линейный конгруэнтный генератор от seed (берём groupNumber как seed)
        val x = seed * 1103515245 + 12345
        val u = ((x ushr 16) and 0xFFFF) / 65535.0 // 0..1
        return value + (u - 0.5) * 0.01           // ±0.005 A
    }

    /**
     * Старый API оставляем, чтобы не разнести проект.
     * Теперь он просто вызывает новую функцию и берёт только группы.
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

        // ✅ 3φ группы: либо уже помечены THREE_PHASE,
        // ✅ либо содержат хотя бы одно 3φ устройство (VoltageType.AC_3PHASE)
        val threePhaseLike = input
            .filter { g ->
                g.phase == Phase.THREE_PHASE ||
                        g.devices.any { it.voltage.type == VoltageType.AC_3PHASE }
            }
            .map { it.copy(phase = Phase.THREE_PHASE) }

        // ✅ В балансировку идут ТОЛЬКО чистые 1φ группы (без 3φ устройств)
        val onePhaseOnly = input.filterNot { g ->
            g.phase == Phase.THREE_PHASE ||
                    g.devices.any { it.voltage.type == VoltageType.AC_3PHASE }
        }

        // Если остались только 3φ — балансировка не нужна
        if (onePhaseOnly.isEmpty()) {
            return threePhaseLike.sortedBy { it.groupNumber } to emptyList()
        }

        val phases = arrayOf(Phase.A, Phase.B, Phase.C)
        val decisions = mutableListOf<DistributionDecision>()

        // 1) Отсортируем по току (тяжёлые раньше), тай-брейк через микрошум
        val sorted = onePhaseOnly.sortedWith(
            compareByDescending<CircuitGroup> { weightWithEpsilon(it.nominalCurrent, it.groupNumber) }
                .thenBy { it.groupNumber }
        )

        // Текущие нагрузки по фазам (A/B/C)
        val loads = doubleArrayOf(0.0, 0.0, 0.0)

        // Списки назначенных групп по фазам
        val assigned = arrayOf(
            mutableListOf<CircuitGroup>(),
            mutableListOf<CircuitGroup>(),
            mutableListOf<CircuitGroup>()
        )

        // 2) Жадная раскладка: кладём каждую группу в «самую лёгкую» фазу с устойчивым тай-брейком
        for (g in sorted) {
            val before = loads.toPhaseMap()
            val idx = loads.withIndex()
                .minBy { weightWithEpsilon(it.value, g.groupNumber) }
                .index

            loads[idx] += g.nominalCurrent
            val updated = g.copy(phase = phases[idx]) // фаза только в результате
            assigned[idx] += updated

            val after = loads.toPhaseMap()
            decisions += DistributionDecision(
                groupNumber = g.groupNumber,
                groupCurrentA = g.nominalCurrent,
                chosenPhase = phases[idx],
                phaseCurrentsBefore = before,
                phaseCurrentsAfter = after,
                algorithm = "balanced_greedy+local_opt",
                note = "Greedy: picked phase with minimal load (tie-break via epsilon)"
            )
        }

        // 3) Локальная оптимизация: пробуем переставлять топ-N тяжёлых между фазами,
        //    уменьшая (max - min)
        val heavy = sorted.take(min(TOP_N, sorted.size))

        repeat(MAX_PASSES) { pass ->
            var improved = false

            for (g in heavy) {
                val srcIdx = findPhaseIndex(assigned, g.groupNumber)
                if (srcIdx == -1) continue

                val currentDelta = currentDelta(loads)

                var bestIdx = srcIdx
                var bestDelta = currentDelta

                // пробуем “вынуть из src и положить в dst”
                val loadIfRemove = loads[srcIdx] - g.nominalCurrent

                for (dstIdx in 0..2) {
                    if (dstIdx == srcIdx) continue

                    val dstLoadBefore = loads[dstIdx]
                    val dstAfter = dstLoadBefore + g.nominalCurrent

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
                    if (delta + EPS < bestDelta) {
                        bestDelta = delta
                        bestIdx = dstIdx
                    }
                }

                if (bestIdx != srcIdx) {
                    val before = loads.toPhaseMap()
                    val from = phases[srcIdx]
                    val to = phases[bestIdx]

                    // применяем перемещение
                    assigned[srcIdx].removeIf { it.groupNumber == g.groupNumber }
                    loads[srcIdx] -= g.nominalCurrent

                    assigned[bestIdx].add(g.copy(phase = to))
                    loads[bestIdx] += g.nominalCurrent

                    val after = loads.toPhaseMap()
                    decisions += DistributionDecision(
                        groupNumber = g.groupNumber,
                        groupCurrentA = g.nominalCurrent,
                        chosenPhase = to,
                        phaseCurrentsBefore = before,
                        phaseCurrentsAfter = after,
                        algorithm = "balanced_greedy+local_opt",
                        note = "LocalOpt(pass=${pass + 1}): moved $from -> $to to reduce delta %.3f -> %.3f"
                            .format(currentDelta, bestDelta)
                    )

                    improved = true
                }
            }

            if (!improved) return@repeat
        }

        // 4) Собираем результат, возвращаем в порядке номеров групп
        val onePhaseResult = (assigned[0] + assigned[1] + assigned[2])
            .sortedBy { it.groupNumber }

        // ✅ 5) Добавляем 3φ обратно (они не участвовали в балансировке)
        val finalGroups = (onePhaseResult + threePhaseLike)
            .sortedBy { it.groupNumber }

        return finalGroups to decisions
    }

    private fun currentDelta(loads: DoubleArray): Double {
        val mx = max(loads[0], max(loads[1], loads[2]))
        val mn = min(loads[0], min(loads[1], loads[2]))
        return mx - mn
    }

    private fun findPhaseIndex(buckets: Array<MutableList<CircuitGroup>>, groupNumber: Int): Int {
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