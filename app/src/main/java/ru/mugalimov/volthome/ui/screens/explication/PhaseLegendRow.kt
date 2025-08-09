package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Phase

@Composable
fun PhaseLegendRow(tag: Phase, current: Double, highlight: Boolean) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(phaseColor(tag), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text("Фаза ${tag.name}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            "%.1f A".format(current),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal
        )
        if (highlight) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun phaseColor(p: Phase): Color = when (p) {
    Phase.A -> Color(0xFFF6D96B)
    Phase.B -> Color(0xFF7ED492)
    Phase.C -> Color(0xFFFF8A80)
}