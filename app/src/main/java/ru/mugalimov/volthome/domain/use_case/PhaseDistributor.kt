package ru.mugalimov.volthome.domain.usecase

import android.content.ContentValues.TAG
import android.util.Log
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseImbalance
import ru.mugalimov.volthome.domain.model.VoltageType

/**
 * Распределяет группы по фазам (L1, L2, L3) так, чтобы суммарный ток на каждой фазе был сбалансирован.
 * Используется простой жадный алгоритм: самая "тяжёлая" группа идёт туда, где сейчас наименьшая суммарная нагрузка.
 *
 * @param groups Список электрических групп без назначенной фазы.
 * @return Список групп, в которых phase уже установлен.
 */
fun distributeGroupsBalanced(groups: List<CircuitGroup>): List<CircuitGroup> {
    // Инициализируем карту фаз с текущей нагрузкой (током) на каждой фазе.
    // Сначала на всех фазах — нулевая нагрузка.
    val phaseLoad = mutableMapOf(
        Phase.A to 0.0,
        Phase.B to 0.0,
        Phase.C to 0.0
    )

    // Здесь будут храниться группы с уже назначенной фазой
    val assignedGroups = mutableListOf<CircuitGroup>()

    Log.d(TAG, "assignedGroups = $assignedGroups")

    // Сортируем входящие группы по убыванию тока.
    // Так мы сначала распределим самые "тяжёлые" группы — это даст лучший баланс.
    val sortedGroups = groups.sortedByDescending { it.nominalCurrent }

    Log.d(TAG, "sortedGroups = $sortedGroups")

    for (group in sortedGroups) {
        // Находим фазу, у которой на данный момент суммарный ток — минимальный.
        val targetPhase = phaseLoad.minByOrNull { it.value }!!.key

        Log.d(TAG, "targetPhase = $targetPhase")

        // Создаём копию группы с назначенной фазой
        val updatedGroup = group.copy(phase = targetPhase)
        Log.d(TAG, "updatedGroup = $updatedGroup")

        // Обновляем суммарную нагрузку на эту фазу
        phaseLoad[targetPhase] = phaseLoad[targetPhase]!! + group.nominalCurrent
        Log.d(TAG, "phaseLoad[targetPhase] = ${phaseLoad[targetPhase]}")

        // Добавляем обновлённую группу в список результата
        assignedGroups.add(updatedGroup)
    }

    return assignedGroups
}

/** Суммирует токи по уже назначенным фазам */
fun phaseCurrents(groups: List<CircuitGroup>): Map<Phase, Double> =
    groups.groupBy { it.phase }
        .mapValues { (_, gs) -> gs.sumOf { it.nominalCurrent } }
        .withDefault { 0.0 }

/** 3Ф, если реально задействовано >= 2 фаз; иначе 1Ф */
fun inferVoltageType(groups: List<CircuitGroup>): VoltageType {
    val used = groups.map { it.phase }.toSet()
    return if (used.size >= 2) VoltageType.AC_3PHASE else VoltageType.AC_1PHASE
}

fun Map<Phase, Double>.getOrZero(phase: Phase): Double = getOrDefault(phase, 0.0)

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
