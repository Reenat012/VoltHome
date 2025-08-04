package ru.mugalimov.volthome.ui.screens.loads



import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowRow
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem


@Composable
fun PhaseGroupTableItem(item: PhaseLoadItem) {
    val phaseColor = when (item.phase) {
        Phase.A -> Color(0xFFFFEB3B) // Жёлтый
        Phase.B -> Color(0xFF4CAF50) // Зелёный
        Phase.C -> Color(0xFFF44336) // Красный
    }

    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Заголовок карточки — кликабелен
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .background(phaseColor.copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color = phaseColor, shape = MaterialTheme.shapes.small)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Фаза ${item.phase.name} — ${item.groups.size} групп",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть"
                )
            }

            // Содержимое карточки (группы), отображается при раскрытии
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(16.dp)) {
                    item.groups.forEachIndexed { index, group ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "Группа №${group.groupNumber} (${group.roomName})",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            FlowRow(
                                mainAxisSpacing = 8.dp,
                                crossAxisSpacing = 8.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                group.devices.forEach { deviceName ->
                                    AssistChip(
                                        onClick = {}, // опционально: можно сделать кликабельным
                                        label = { Text(deviceName) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Мощность: ${group.totalPower.toInt()} Вт, Ток: %.2f А".format(group.totalCurrent),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (index != item.groups.lastIndex) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}




