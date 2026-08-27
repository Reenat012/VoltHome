package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.UiPhase
import ru.mugalimov.volthome.core.theme.UiStatus
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.use_case.BalanceStatus
import ru.mugalimov.volthome.domain.use_case.balanceStatus
import ru.mugalimov.volthome.domain.use_case.calcPhaseBalance
import ru.mugalimov.volthome.ui.format.UiTextFormat

/**
 * Компактная сравнительная сводка фаз. Имя сохранено, чтобы не ломать внешний API
 * экрана, но круговая диаграмма намеренно заменена шкалами: длины трёх значений
 * легче сравнить, чем углы секторов.
 */
@Composable
fun ThreePhaseBalanceOverview(
    perPhase: Map<Phase, Double>,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val a = perPhase[Phase.A] ?: 0.0
    val b = perPhase[Phase.B] ?: 0.0
    val c = perPhase[Phase.C] ?: 0.0
    val total = a + b + c
    val maxCurrent = maxOf(a, b, c).coerceAtLeast(0.001)
    val balance = calcPhaseBalance(a, b, c)
    val status = balanceStatus(balance.pct)
    val statusText = when (status) {
        BalanceStatus.OK -> "Баланс в норме"
        BalanceStatus.MINOR -> "Есть небольшой перекос"
        BalanceStatus.HIGH -> "Нужна проверка"
    }
    val statusColor = VhColors.status(
        when (status) {
            BalanceStatus.OK -> UiStatus.SUCCESS
            BalanceStatus.MINOR -> UiStatus.WARNING
            BalanceStatus.HIGH -> UiStatus.ERROR
        }
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Баланс фаз",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Сравнение расчётных токов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BalanceStatusPill(statusText, statusColor)
            }

            PhaseBar(Phase.A, a, total, maxCurrent, animate, VhColors.phase(UiPhase.A))
            PhaseBar(Phase.B, b, total, maxCurrent, animate, VhColors.phase(UiPhase.B))
            PhaseBar(Phase.C, c, total, maxCurrent, animate, VhColors.phase(UiPhase.C))

            Box(
                modifier = Modifier.fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Разница токов",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${UiTextFormat.amperes(maxOf(a, b, c) - minOf(a, b, c))} · ${fmt0(balance.pct)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun PhaseBar(
    phase: Phase,
    current: Double,
    total: Double,
    maximum: Double,
    animate: Boolean,
    color: Color
) {
    val target = (current / maximum).coerceIn(0.0, 1.0).toFloat()
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(if (animate) 550 else 0),
        label = "phase_${phase.name}"
    )
    val share = if (total > 0.0) current / total * 100.0 else 0.0

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(color, CircleShape))
                Text("Фаза ${phase.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text(
                text = "${UiTextFormat.amperes(current)} · ${fmt0(share)}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(progress).height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun BalanceStatusPill(text: String, color: Color) {
    Row(
        modifier = Modifier.background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

private fun fmt0(value: Double) = UiTextFormat.decimal(value, digits = 0)
