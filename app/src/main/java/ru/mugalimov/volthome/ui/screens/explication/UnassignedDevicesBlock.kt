package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Device

/**
 * Коммит 5: контейнер "Нераспределённые устройства"
 *
 * ВАЖНО:
 * - source of truth по составу: draftState.unassignedDeviceIds (на экране)
 * - список Device сюда приходит уже резолвнутым (VM)
 */
@Composable
fun UnassignedDevicesBlock(
    devices: List<Device>,
    onAutoAssignClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Нераспределённые устройства",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(onClick = onAutoAssignClick) {
                    Text("Распределить автоматически…")
                }
            }

            Spacer(Modifier.height(8.dp))

            // ⚠️ Текст подсказки — держим одной строкой, чтобы grep был железный.
            Text(
                text = UNASSIGNED_HINT_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text(
                    text = "Нет нераспределённых устройств.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                devices
                    .sortedBy { it.name.trim().lowercase() }
                    .forEach { d ->
                        Text(
                            text = "• ${d.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
            }
        }
    }
}

private const val UNASSIGNED_HINT_TEXT =
    "Если устройство не относится ни к одной группе, оно попадает сюда. Нажмите «Распределить автоматически…», чтобы восстановить распределение."