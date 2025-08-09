package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

@Composable
fun PhaseGroupTableItem(
    item: PhaseLoadItem,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    // Цвета как у доната, но приглушённые
    val phaseAccent = when (item.phase) {
        Phase.A -> Color(0xFFF6D96B).copy(alpha = 0.25f) // жёлтый
        Phase.B -> Color(0xFF7ED492).copy(alpha = 0.25f) // зелёный
        Phase.C -> Color(0xFFFF8A80).copy(alpha = 0.25f) // красный
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = phaseAccent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // без тени
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Фаза ${item.phase.name}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = { Text("${item.groups.size}") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Power,
                            contentDescription = null
                        )
                    }
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    item.groups.forEachIndexed { index, group ->
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = "Группа №${group.groupNumber} (${group.roomName})",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                group.devices.forEach { deviceName ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(deviceName) },
                                        border = AssistChipDefaults.assistChipBorder(false)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "${group.totalPower.toInt()} Вт • ${"%.2f".format(group.totalCurrent)} A",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (index != item.groups.lastIndex) {
                                Spacer(Modifier.height(12.dp))
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}




