package ru.mugalimov.volthome.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.ProjectCoverage

@Composable
fun UnassignedDevicesWarningCard(
    coverage: ProjectCoverage,
    modifier: Modifier = Modifier
) {
    if (coverage.isComplete) return

    val cs = MaterialTheme.colorScheme
    val examples = coverage.unassignedDevices
        .take(3)
        .joinToString { it.name }
    val remaining = (coverage.unassignedCount - 3).coerceAtLeast(0)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.errorContainer.copy(alpha = 0.56f)),
        border = BorderStroke(1.dp, cs.error.copy(alpha = 0.42f)),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = cs.error
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Вне расчёта: ${coverage.unassignedCount} ${deviceWord(coverage.unassignedCount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onErrorContainer
                )
                Text(
                    text = "Проверьте состав проекта: устройство может требовать ручного распределения или линия выходит за поддерживаемую матрицу до 160 А. Итоговая нагрузка и комплектация щита могут быть неполными.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onErrorContainer
                )
                if (examples.isNotBlank()) {
                    Text(
                        text = examples + if (remaining > 0) " и ещё $remaining" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onErrorContainer.copy(alpha = 0.82f)
                    )
                }
            }
        }
    }
}

private fun deviceWord(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "устройств"
        mod10 == 1 -> "устройство"
        mod10 in 2..4 -> "устройства"
        else -> "устройств"
    }
}
