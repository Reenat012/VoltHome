package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Phase

@Composable
fun PhaseHeader(phase: Phase) {
    val bg = phaseColor(phase).copy(alpha = 0.22f)
    val stroke = phaseColor(phase).copy(alpha = 0.65f)

    Surface(
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        color = bg,
        border = BorderStroke(1.dp, stroke)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("Фаза ${phase.name}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// Единые цвета фаз
private fun phaseColor(p: Phase): Color = when (p) {
    Phase.A -> Color(0xFFF6D96B) // жёлтый
    Phase.B -> Color(0xFF7ED492) // зелёный
    Phase.C -> Color(0xFFFF8A80) // красный
}