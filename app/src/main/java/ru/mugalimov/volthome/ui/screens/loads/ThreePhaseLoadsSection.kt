package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Power
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

@Composable
fun ThreePhaseLoadsSection(
    item: PhaseLoadItem,
    decisionsByGroupNumber: Map<Int, List<DistributionDecision>>,
    modifier: Modifier = Modifier,

    // ✅ теперь не используется (оставлено для совместимости с вызовами)
    onDecisionDetailsClick: (groupNumber: Int) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Outlined.Bolt, contentDescription = null)

                Text(
                    text = "3-фазные нагрузки",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.weight(1f))

                AssistChip(
                    onClick = {},
                    label = { Text("${item.groups.size}") },
                    leadingIcon = { Icon(imageVector = Icons.Outlined.Power, contentDescription = null) },
                    border = AssistChipDefaults.assistChipBorder(false)
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
                Column {
                    Text(
                        text = "${item.totalPower.toInt()} Вт • ${"%.2f".format(item.totalCurrent)} A",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item.groups.forEachIndexed { index, group ->
                            Column {
                                Text(
                                    text = "Группа №${group.groupNumber} (${group.roomName})",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(8.dp))

                                val events = decisionsByGroupNumber[group.groupNumber].orEmpty()
                                val decision = events.lastOrNull()

                                if (decision != null) {
                                    val ui = decision.toDecisionExplanationUi()

                                    // ✅ Локальное состояние раскрытия "Подробнее" (теперь это B)
                                    var detailsExpanded by remember(group.groupId) { mutableStateOf(false) }

                                    // A) Заголовок (всегда)
                                    Text(
                                        text = ui.levelA_title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // A) Метрика (всегда, если есть)
                                    if (ui.levelA_metric.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = ui.levelA_metric,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    // ✅ "Подробнее" → раскрывает/скрывает уровень B
                                    Text(
                                        text = if (detailsExpanded) "Скрыть" else "Подробнее",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { detailsExpanded = !detailsExpanded }
                                    )

                                    // ✅ Уровень B показываем только при detailsExpanded
                                    AnimatedVisibility(
                                        visible = detailsExpanded,
                                        enter = fadeIn() + expandVertically(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(Modifier.padding(top = 6.dp)) {

                                            // B) Причина
                                            if (ui.levelB_reason.isNotBlank()) {
                                                Text(
                                                    text = ui.levelB_reason,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Spacer(Modifier.height(2.dp))

                                            // B) До/После
                                            Text(
                                                text = ui.levelB_before,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = ui.levelB_after,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    group.devices.forEach { device ->
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(device.name) },
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
}