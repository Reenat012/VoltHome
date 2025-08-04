package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

@Composable
fun PhaseLoadDonutChart(
    items: List<PhaseLoadItem>,
    modifier: Modifier = Modifier
) {
    val totalCurrent = items.sumOf { it.totalCurrent }
    val stroke = 40f

    // Цвета по ПУЭ
    val phaseColors = mapOf(
        Phase.A to Color(0xFFFFEB3B), // Жёлтый
        Phase.B to Color(0xFF4CAF50), // Зелёный
        Phase.C to Color(0xFFF44336)  // Красный
    )

    val orderedPhases = listOf(Phase.A, Phase.B, Phase.C)
    val orderedItems = orderedPhases.mapNotNull { phase -> items.find { it.phase == phase } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Диаграмма
        Box(
            modifier = Modifier
                .size(220.dp)
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -135f

                orderedItems.forEach { item ->
                    val sweepAngle = (item.totalCurrent / totalCurrent * 360f).toFloat()

                    drawArc(
                        color = phaseColors[item.phase] ?: Color.Gray,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = stroke)
                    )
                    startAngle += sweepAngle
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.1f A".format(totalCurrent),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Суммарно",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Легенда (выведена отдельно, без ограничений по высоте)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            orderedItems.forEach { item ->
                val color = phaseColors[item.phase] ?: Color.Gray

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(color, shape = MaterialTheme.shapes.extraSmall)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Фаза ${item.phase.name}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                    Text(
                        text = "%.1f А".format(item.totalCurrent),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}




