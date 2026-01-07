package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.core.theme.toUiPhase
import ru.mugalimov.volthome.domain.model.Phase

@Composable
fun PhaseHeader(phase: Phase) {
    val ui = phase.toUiPhase()
    val bg = VhColors.phaseSurface(ui).copy(alpha = 0.22f)
    val stroke = VhColors.phaseBorder(ui).copy(alpha = 0.65f)

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