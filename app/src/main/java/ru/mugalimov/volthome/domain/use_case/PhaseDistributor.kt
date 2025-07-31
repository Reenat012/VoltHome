package ru.mugalimov.volthome.domain.usecase

import android.content.ContentValues.TAG
import android.util.Log
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase

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
