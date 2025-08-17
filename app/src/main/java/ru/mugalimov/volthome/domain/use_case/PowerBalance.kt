package ru.mugalimov.volthome.domain.use_case

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseBalance

/**
 * Рассчитывает дисбаланс фаз:
 * pct = (Imax - Imin) / Iavg * 100%, если Iavg > 0; иначе 0.
 * Возвращает также фазы с макс/мин током и среднее.
 */
fun calcPhaseBalance(a: Double, b: Double, c: Double): PhaseBalance {
    val ia = abs(a)
    val ib = abs(b)
    val ic = abs(c)

    val avg = (ia + ib + ic) / 3.0
    if (avg <= 0.0) {
        // Нулевые токи — считаем идеальным балансом
        return PhaseBalance(
            pct = 0.0,
            maxPhase = Phase.A,
            minPhase = Phase.A,
            avg = 0.0
        )
    }

    // Детерминированный выбор max/min при равенствах:
    // сначала сравниваем по значению, затем по фиксированному порядку фаз A<B<C
    val order = compareBy<Pair<Phase, Double>>({ it.second }).thenBy { phaseOrder(it.first) }
    val triples = listOf(Phase.A to ia, Phase.B to ib, Phase.C to ic)

    // maxWithOrNull/minWithOrNull НЕ используют vararg и не требуют Comparable у Pair
    val (maxPhase, maxI) = triples.maxWithOrNull(order)!!
    val (minPhase, minI) = triples.minWithOrNull(order)!!

    val delta = maxI - minI
    val pct = if (delta <= 1e-9) 0.0 else (delta / avg) * 100.0

    return PhaseBalance(
        pct = pct,
        maxPhase = maxPhase,
        minPhase = minPhase,
        avg = avg
    )
}

/** Фиксированный порядок фаз для tie-break (A < B < C) */
private fun phaseOrder(p: Phase): Int = when (p) {
    Phase.A -> 0
    Phase.B -> 1
    Phase.C -> 2
}

enum class BalanceLevel { OK, MINOR, HIGH }

/** Пороговые уровни дисбаланса. При необходимости вынеси в конфиг/константы. */
fun balanceLevel(pct: Double): BalanceLevel = when {
    pct < 10.0 -> BalanceLevel.OK       // <10% — норма
    pct < 25.0 -> BalanceLevel.MINOR    // 10..25% — умеренный перекос
    else       -> BalanceLevel.HIGH     // >25% — сильный перекос
}

/**
 * (Опционально для UI) Возвращает текст/цвет для отображения уровня баланса.
 * Если держишь domain «чистым», перенеси эту функцию в UI-модуль.
 */
@Composable
fun balanceUi(pct: Double): Pair<String, Color> = when (balanceLevel(pct)) {
    BalanceLevel.OK   -> "Баланс в норме"                  to MaterialTheme.colorScheme.primary
    BalanceLevel.MINOR-> "Небольшой перекос"   to MaterialTheme.colorScheme.tertiary
    BalanceLevel.HIGH -> "Сильный перекос"                 to MaterialTheme.colorScheme.error
}