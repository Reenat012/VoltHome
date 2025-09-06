package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.use_case.balanceUi
import ru.mugalimov.volthome.domain.use_case.calcPhaseBalance

@Composable
fun PhaseLoadDonutChart(
    perPhase: Map<Phase, Double>,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 18.dp,
    gapDeg: Float = 2f,
    showLegend: Boolean = true,
    animate: Boolean = true,
    chartSizeDp: Dp = 220.dp,
    mode: PhaseMode = PhaseMode.THREE,
    incomerRating: Int? = null, // номинал вводного; нужен только в 1-фазе
    warnPct: Int = 60,          // ≤warn — зелёный
    alertPct: Int = 80          // ≥alert — красный
) {
    val a = perPhase[Phase.A] ?: 0.0
    val b = perPhase[Phase.B] ?: 0.0
    val c = perPhase[Phase.C] ?: 0.0

    val colorSurfaceVariant = colorScheme.surfaceVariant

    // ===== 1-фаза: индикатор загрузки вводного =====
    if (mode == PhaseMode.SINGLE) {
        val totalRated = (incomerRating ?: 0).coerceAtLeast(1)
        val pct = ((a / totalRated) * 100.0).coerceIn(0.0, 100.0)

        val color = when {
            pct >= alertPct -> MaterialTheme.colorScheme.error
            pct >= warnPct  -> MaterialTheme.colorScheme.tertiary
            else            -> MaterialTheme.colorScheme.primary
        }

        val progress by animateFloatAsState(
            targetValue = pct.toFloat() / 100f,
            animationSpec = tween(durationMillis = if (animate) 800 else 0),
            label = "progress"
        )

        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(chartSizeDp)) {
                val stroke = Stroke(width = ringWidth.toPx(), cap = StrokeCap.Round)

                // фон
                drawArc(
                    color = colorSurfaceVariant,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke,
                    size = size
                )
                // заполнение
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = stroke,
                    size = size
                )
            }

            // центр: только "A из I_incomer" и "%", без длинных текстов
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${fmt1(a)} A из $totalRated A",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${fmt0(pct)}%",
                    color = color,
                    style = MaterialTheme.typography.bodyMedium
                )
                val reserveA = (totalRated - a).coerceAtLeast(0.0)
                Text(
                    text = "Запас: ${fmt1(reserveA)} A",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // длинные тексты — НИЖЕ круга, чтобы не залезали на дугу
        val reserveA = (totalRated - a).coerceAtLeast(0.0)
        Spacer(Modifier.height(6.dp))

        if (showLegend) {
            Spacer(Modifier.height(12.dp))
            // мини-легенда своей реализацией
            LegendRow(
                label = "Фаза A",
                amps = a,
                total = a.coerceAtLeast(0.0001), // чтобы было 100%
                color = MaterialTheme.colorScheme.primary,
                bold = true
            )
        }
        return
    }

    // ===== 3-фазы: классический донат =====
    val bal = calcPhaseBalance(a, b, c)
    val (statusText, statusColor) = balanceUi(bal.pct)

    val values = listOf(a, b, c)
    val baseColors = listOf(
        Color(0xFFF6D96B), // A — жёлтый
        Color(0xFF7ED492), // B — зелёный
        Color(0xFFFF8A80)  // C — красный
    )
    val colors = listOf(Phase.A, Phase.B, Phase.C).mapIndexed { i, _ ->
        if (listOf(Phase.A, Phase.B, Phase.C)[i] == bal.maxPhase) baseColors[i]
        else baseColors[i].copy(alpha = 0.6f)
    }

    val ringBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(if (animate) 700 else 0),
        label = "donutProgress"
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(chartSizeDp)
                    .padding(12.dp)
            ) {
                val total = values.sum().toFloat()
                val stroke = Stroke(width = ringWidth.toPx(), cap = StrokeCap.Round)

                // фон кольца
                drawArc(
                    color = ringBg,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke,
                    size = size
                )

                if (total <= 0f) {
                    drawArc(
                        color = outlineColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke,
                        size = size
                    )
                    return@Canvas
                }

                val minSweep = 0.8f
                var start = -90f
                values.forEachIndexed { i, vD ->
                    val v = vD.toFloat()
                    var sweep = (v / total) * 360f * progress
                    if (sweep < minSweep) sweep = minSweep

                    val adjSweep = (sweep - gapDeg).coerceAtLeast(0f)
                    val adjStart = start + gapDeg / 2f

                    drawArc(
                        color = colors[i],
                        startAngle = adjStart,
                        sweepAngle = adjSweep,
                        useCenter = false,
                        style = Stroke(width = ringWidth.toPx(), cap = StrokeCap.Round),
                        size = size
                    )
                    start += sweep
                }
            }

            // центр: метрика перекоса + статус
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val delta = (maxOf(a, b, c) - minOf(a, b, c))
                Text(
                    text = "ΔI ${fmt1(delta)} A (${fmt0(bal.pct)}%)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (showLegend) {
            Spacer(Modifier.height(8.dp))
            PhaseLegend(
                a = a, b = b, c = c,
                total = (a + b + c),
                worst = bal.maxPhase
            )
        }
    }
}

@Composable
private fun PhaseLegend(a: Double, b: Double, c: Double, total: Double, worst: Phase) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.fillMaxWidth(0.9f)
    ) {
        LegendRow("Фаза A", a, total, Color(0xFFF6D96B), bold = worst == Phase.A)
        LegendRow("Фаза B", b, total, Color(0xFF7ED492), bold = worst == Phase.B)
        LegendRow("Фаза C", c, total, Color(0xFFFF8A80), bold = worst == Phase.C)
    }
}

@Composable
private fun LegendRow(label: String, amps: Double, total: Double, color: Color, bold: Boolean) {
    val pct = if (total > 0.0) amps / total * 100.0 else 0.0
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = "${fmt1(amps)} A  •  ${fmt0(pct)}%",
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun fmt1(v: Double) = String.format("%.1f", v)
private fun fmt0(v: Double) = String.format("%.0f", v)