package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.core.theme.toUiPhase
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

@Composable
fun PhaseGroupTableItem(
    item: PhaseLoadItem,
    decisionsByGroupNumber: Map<Int, List<DistributionDecision>>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRegisterDropZone: (Phase, Rect) -> Unit,
    onDragStartAttempt: (payload: PhaseLoadContentKt_DragPayload, startRoot: Offset) -> Unit,
    onDragMove: (rootPos: Offset) -> Unit,
    onDragEndAttempt: (payload: PhaseLoadContentKt_DragPayload) -> Unit,
    isDropTargetHighlighted: Boolean,
    onDragCancel: () -> Unit,

    // ✅ теперь не используется (оставлено для совместимости с вызовами)
    onDecisionDetailsClick: (groupNumber: Int) -> Unit = {}
) {
    val uiPhase = item.phase.toUiPhase()
    val phaseAccent = VhColors.phase(uiPhase).copy(alpha = 0.25f)

    val highlightAlpha = if (isDropTargetHighlighted) 0.55f else 0.25f
    val borderColor = if (isDropTargetHighlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    val borderWidth = if (isDropTargetHighlighted) 2.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .border(borderWidth, borderColor, MaterialTheme.shapes.large)
            .onGloballyPositioned { coords ->
                onRegisterDropZone(item.phase, coords.boundsInRoot())
            },
        colors = CardDefaults.cardColors(containerColor = phaseAccent.copy(alpha = highlightAlpha)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDropTargetHighlighted) 4.dp else 0.dp),
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
                    leadingIcon = { Icon(Icons.Outlined.Power, contentDescription = null) }
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
                        var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                        Column(Modifier.padding(vertical = 8.dp)) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Группа №${group.groupNumber} (${group.roomName})",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier.weight(1f)
                                )

                                val payload = PhaseLoadContentKt_DragPayload(
                                    groupId = group.groupId,
                                    fromPhase = item.phase,
                                    title = "Группа №${group.groupNumber} (${group.roomName})"
                                )

                                IconButton(
                                    onClick = { /* drag only */ },
                                    modifier = Modifier
                                        .onGloballyPositioned { handleCoords = it }
                                        .pointerInput(group.groupId) {
                                            detectDragGestures(
                                                onDragStart = { startLocal ->
                                                    val c = handleCoords ?: return@detectDragGestures
                                                    val startRoot = c.localToRoot(startLocal)
                                                    onDragStartAttempt(payload, startRoot)
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val c = handleCoords ?: return@detectDragGestures
                                                    onDragMove(c.localToRoot(change.position))
                                                },
                                                onDragCancel = { onDragCancel() },
                                                onDragEnd = { onDragEndAttempt(payload) }
                                            )
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DragIndicator,
                                        contentDescription = "Перетащить группу"
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                group.devices.forEach { deviceItem ->
                                    key(deviceItem.deviceId) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(deviceItem.name) },
                                            border = AssistChipDefaults.assistChipBorder(false)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "${group.totalPower.toInt()} Вт • ${"%.2f".format(group.totalCurrent)} A",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val events = decisionsByGroupNumber[group.groupNumber].orEmpty()
                            val decision = events.lastOrNull()

                            if (decision != null) {
                                val ui = decision.toDecisionExplanationUi()

                                // ✅ Локальное состояние раскрытия "Подробнее" (теперь это B)
                                var detailsExpanded by remember(group.groupId) { mutableStateOf(false) }

                                Spacer(Modifier.height(6.dp))

                                // A: заголовок (всегда)
                                Text(
                                    text = ui.levelA_title,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // A: метрика (всегда, если есть)
                                if (ui.levelA_metric.isNotBlank()) {
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
                                    modifier = Modifier.clickable { detailsExpanded = !detailsExpanded },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // ✅ Уровень B показываем только при detailsExpanded
                                AnimatedVisibility(
                                    visible = detailsExpanded,
                                    enter = fadeIn() + expandVertically(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(Modifier.padding(top = 6.dp)) {

                                        // B: причина
                                        if (ui.levelB_reason.isNotBlank()) {
                                            Text(
                                                text = ui.levelB_reason,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // B: до/после
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
                            }

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

/**
 * Общий payload для DnD.
 */
data class PhaseLoadContentKt_DragPayload(
    val groupId: Long,
    val fromPhase: Phase,
    val title: String
)