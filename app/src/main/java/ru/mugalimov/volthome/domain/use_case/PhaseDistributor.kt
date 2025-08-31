package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Распределяет однофазные группы по фазам A/B/C так, чтобы минимизировать перекос.
 * Алгоритм: жадная раскладка + локальная оптимизация topN «тяжёлых» групп.
 *
 * Важно:
 * - Номера групп не меняем.
 * - phase переприсваиваем только в результате.
 */
object PhaseDistributor {

    // ⬇ изменено: чуть глубже локальная оптимизация
    private const val TOP_N = 15
    private const val MAX_PASSES = 3

    /**
     * Крошечный детерминированный «шум» для устойчивых тай-брейков при равных нагрузках.
     * Не влияет на итоговые суммы заметно (±0.005 A), но убирает прилипание к фазе A.
     */
    // ⬇ добавлено
    private fun weightWithEpsilon(value: Double, seed: Int): Double {
        // Линейный конгруэнтный генератор от seed (берём groupNumber как seed)
        val x = seed * 1103515245 + 12345
        val u = ((x ushr 16) and 0xFFFF) / 65535.0 // 0..1
        return value + (u - 0.5) * 0.01           // ±0.005 A
    }

    fun distributeGroupsBalanced(input: List<CircuitGroup>): List<CircuitGroup> {
        if (input.isEmpty()) return emptyList()

        val phases = arrayOf(Phase.A, Phase.B, Phase.C)

        // 1) Отсортируем по току (тяжёлые раньше), тай-брейк через микрошум
        // ⬇ изменено
        val sorted = input.sortedWith(compareByDescending<CircuitGroup> {
            weightWithEpsilon(it.nominalCurrent, it.groupNumber)
        }.thenBy { it.groupNumber })

        // Текущие нагрузки по фазам
        val loads = doubleArrayOf(0.0, 0.0, 0.0)

        // Списки назначенных групп по фазам
        val assigned = arrayOf(mutableListOf<CircuitGroup>(), mutableListOf<CircuitGroup>(), mutableListOf<CircuitGroup>())

        // 2) Жадная раскладка: кладём каждую группу в «самую лёгкую» фазу с устойчивым тай-брейком
        // ⬇ изменено
        for (g in sorted) {
            val idx = loads.withIndex().minBy { weightWithEpsilon(it.value, g.groupNumber) }.index
            loads[idx] += g.nominalCurrent
            assigned[idx] += g.copy(phase = phases[idx]) // фаза только в результате
        }

        // 3) Локальная оптимизация: пробуем переставлять топ-N тяжёлых между фазами
        //    уменьшая (max - min)
        // ⬇ усилено
        val heavy = sorted.take(min(TOP_N, sorted.size)).toMutableList()

        repeat(MAX_PASSES) {
            var improved = false

            // перебор кандидатных перестановок: берём тяжёлую группу и пробуем переместить её в другую фазу,
            // если это уменьшает перекос
            for (g in heavy) {
                val srcIdx = findPhaseIndex(assigned, g.groupNumber)
                if (srcIdx == -1) continue

                val srcLoadBefore = loads[srcIdx]
                val loadIfRemove = srcLoadBefore - g.nominalCurrent

                var bestIdx = srcIdx
                var bestDelta = currentDelta(loads)

                for (dstIdx in 0..2) {
                    if (dstIdx == srcIdx) continue
                    val dstLoadBefore = loads[dstIdx]
                    val maxAfter = maxOf(
                        if (srcIdx == 0) loadIfRemove else loads[0],
                        if (srcIdx == 1) loadIfRemove else loads[1],
                        if (srcIdx == 2) loadIfRemove else loads[2],
                        if (dstIdx == 0) dstLoadBefore + g.nominalCurrent else loads[0],
                        if (dstIdx == 1) dstLoadBefore + g.nominalCurrent else loads[1],
                        if (dstIdx == 2) dstLoadBefore + g.nominalCurrent else loads[2],
                    )
                    val minAfter = minOf(
                        if (srcIdx == 0) loadIfRemove else loads[0],
                        if (srcIdx == 1) loadIfRemove else loads[1],
                        if (srcIdx == 2) loadIfRemove else loads[2],
                        if (dstIdx == 0) dstLoadBefore + g.nominalCurrent else loads[0],
                        if (dstIdx == 1) dstLoadBefore + g.nominalCurrent else loads[1],
                        if (dstIdx == 2) dstLoadBefore + g.nominalCurrent else loads[2],
                    )
                    val delta = maxAfter - minAfter
                    if (delta + 1e-9 < bestDelta) {
                        bestDelta = delta
                        bestIdx = dstIdx
                    }
                }

                if (bestIdx != srcIdx) {
                    // применяем перемещение
                    assigned[srcIdx].removeIf { it.groupNumber == g.groupNumber }
                    loads[srcIdx] -= g.nominalCurrent

                    assigned[bestIdx].add(g.copy(phase = phases[bestIdx]))
                    loads[bestIdx] += g.nominalCurrent

                    improved = true
                }
            }

            if (!improved) return@repeat
        }

        // 4) Собираем результат, возвращаем в порядке номеров групп (как у тебя принято)
        val result = (assigned[0] + assigned[1] + assigned[2])
            .sortedBy { it.groupNumber }

        return result
    }

    private fun currentDelta(loads: DoubleArray): Double {
        val max = max(loads[0], max(loads[1], loads[2]))
        val min = min(loads[0], min(loads[1], loads[2]))
        return max - min
    }

    private fun findPhaseIndex(buckets: Array<MutableList<CircuitGroup>>, groupNumber: Int): Int {
        for (i in buckets.indices) {
            if (buckets[i].any { it.groupNumber == groupNumber }) return i
        }
        return -1
    }
}