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

    val colors = mapOf(
        Phase.A to MaterialTheme.colorScheme.primary,
        Phase.B to MaterialTheme.colorScheme.secondary,
        Phase.C to MaterialTheme.colorScheme.tertiary
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(180.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f

                items.forEach { item ->
                    val sweepAngle = (item.totalCurrent / totalCurrent * 360f).toFloat()

                    drawArc(
                        color = colors[item.phase] ?: Color.Gray,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = stroke)
                    )
                    startAngle += sweepAngle
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Легенда
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .padding(end = 8.dp)
                        .background(
                            color = colors[item.phase] ?: Color.Gray,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                )
                Text(
                    text = "Фаза ${item.phase.name} — %.1f A".format(item.totalCurrent),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}
