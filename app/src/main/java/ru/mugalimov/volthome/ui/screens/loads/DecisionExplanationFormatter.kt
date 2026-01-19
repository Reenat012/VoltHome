package ru.mugalimov.volthome.ui.screens.loads

import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.Phase

/**
 * Коммит 3:
 * Форматтер уровня C: "Аудит решения" без повторов A/B.
 *
 * Требования:
 * - Никаких: "Почему фаза", "Причина:", "До:", "После:", "ΔI".
 * - Никаких англ-слов: Greedy, tie-break, epsilon, LocalOpt.
 * - Никакого decision.note.
 *
 * Источник: список событий DistributionDecision (history).
 */
object DecisionDetailsFormatter {

    data class C(
        val title: String,
        val lines: List<String>
    )

    /**
     * ВАЖНО: именно этот метод вызывают UI-композаблы:
     * DecisionDetailsFormatter.formatC(events)
     */
    fun formatC(events: List<DistributionDecision>): C {
        if (events.isEmpty()) {
            return C(
                title = "Аудит решения",
                lines = listOf("Нет истории решения для этой группы.")
            )
        }

        val moves = extractMoves(events)
        val phaseChanges = countPhaseChanges(events)

        val lines = buildList {
            // C1 — процесс (кратко и по делу)
            add("Алгоритм: балансировка нагрузки по фазам")
            add(
                when {
                    phaseChanges == 0 -> "Шаги: базовая раскладка"
                    else -> "Шаги: базовая раскладка + уточнение распределения"
                }
            )

            // C2 — перемещения (если были)
            if (moves.isNotEmpty()) {
                add("")
                add("Перемещения группы:")
                moves.forEachIndexed { idx, m ->
                    add("${idx + 1}) ${m.from.name} → ${m.to.name}")
                }
            }

            // C3 — устойчивость / повторяемость
            add("")
            addAll(buildStabilityBlock(events, phaseChanges, moves.isNotEmpty()))

            // Усиление ценности, если реально ничего не двигали
            if (moves.isEmpty()) {
                add("")
                add("Оптимизация не потребовалась: базовая раскладка уже давала приемлемый результат.")
            }
        }

        return C(
            title = "Аудит решения",
            lines = lines
        )
    }

    private data class Move(val from: Phase, val to: Phase)

    private fun extractMoves(events: List<DistributionDecision>): List<Move> {
        if (events.size <= 1) return emptyList()
        val moves = mutableListOf<Move>()
        var prev = events.first().chosenPhase
        for (i in 1 until events.size) {
            val cur = events[i].chosenPhase
            if (cur != prev) moves += Move(from = prev, to = cur)
            prev = cur
        }
        return moves
    }

    private fun countPhaseChanges(events: List<DistributionDecision>): Int {
        if (events.size <= 1) return 0
        var changes = 0
        var prev = events.first().chosenPhase
        for (i in 1 until events.size) {
            val cur = events[i].chosenPhase
            if (cur != prev) changes++
            prev = cur
        }
        return changes
    }

    private fun buildStabilityBlock(
        events: List<DistributionDecision>,
        phaseChanges: Int,
        hasMoves: Boolean
    ): List<String> {
        if (events.size <= 1) {
            return listOf("Устойчивость: решение получено без дополнительных итераций.")
        }

        return when {
            phaseChanges == 0 && !hasMoves -> listOf(
                "Устойчивость: дополнительных попыток не было — результат получен сразу."
            )
            phaseChanges == 0 -> listOf(
                "Устойчивость: выбор фазы не менялся при уточнении — результат стабильный."
            )
            phaseChanges in 1..2 -> listOf(
                "Устойчивость: были проверены альтернативы, итоговое решение закрепилось."
            )
            else -> listOf(
                "Устойчивость: было несколько попыток улучшения, итог выбран по лучшему балансу."
            )
        }
    }
}