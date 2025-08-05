package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem


@Composable
fun PhaseGroupTableItem(item: PhaseLoadItem) {
    val phaseBackgrounds = mapOf(
        Phase.A to Color(0xFFFFFDE7), // Light Yellow
        Phase.B to Color(0xFFE8F5E9), // Light Green
        Phase.C to Color(0xFFFFEBEE)  // Light Red
    )

    val phaseColors = mapOf(
        Phase.A to Color(0xFFFFF176), // Yellow
        Phase.B to Color(0xFF81C784), // Green
        Phase.C to Color(0xFFE57373)  // Red
    )

    val bgColor = phaseBackgrounds[item.phase] ?: MaterialTheme.colorScheme.surface
    val dotColor = phaseColors[item.phase] ?: Color.Gray

    // ✅ Управление состоянием раскрытия
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Заголовок карточки с кликом
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(dotColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Фаза ${item.phase.name} — ${item.groups.size} групп",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Скрыть" else "Показать"
                )
            }

            // ✅ Содержимое карточки раскрывается по isExpanded
            if (isExpanded) {
                Divider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    item.groups.forEachIndexed { index, group ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = "Группа №${group.groupNumber} (${group.roomName})",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                group.devices.forEach { device ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(device) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Мощность: ${group.totalPower.toInt()} Вт, Ток: %.2f А".format(group.totalCurrent),
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (index != item.groups.lastIndex) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}




