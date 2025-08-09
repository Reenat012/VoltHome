package ru.mugalimov.volthome.domain.use_case

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseBalance

fun calcPhaseBalance(a: Double, b: Double, c: Double): PhaseBalance {
    val minI = minOf(a, b, c)
    val maxI = maxOf(a, b, c)
    val avgI = (a + b + c) / 3.0
    val pct = if (avgI <= 0.0) 0.0 else (maxI - minI) / avgI * 100.0
    val maxP = when (maxI) { a -> Phase.A; b -> Phase.B; else -> Phase.C }
    val minP = when (minI) { a -> Phase.A; b -> Phase.B; else -> Phase.C }
    return PhaseBalance(pct = pct, maxPhase = maxP, minPhase = minP, avg = avgI)
}

enum class BalanceLevel { OK, MINOR, HIGH }

fun balanceLevel(pct: Double): BalanceLevel = when {
    pct < 10.0 -> BalanceLevel.OK
    pct < 25.0 -> BalanceLevel.MINOR
    else       -> BalanceLevel.HIGH
}

@Composable
fun balanceUi(pct: Double): Pair<String, Color> = when (balanceLevel(pct)) {
    BalanceLevel.OK    -> "Баланс! Всё ок!" to MaterialTheme.colorScheme.primary
    BalanceLevel.MINOR -> "Небольшой перекос! Не критично" to MaterialTheme.colorScheme.tertiary
    BalanceLevel.HIGH  -> "Сильный перекос! Плохо" to MaterialTheme.colorScheme.error
}