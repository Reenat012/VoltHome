package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.core.theme.toUiPhase
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import ru.mugalimov.volthome.ui.format.UiTextFormat

@Composable
fun PhaseGroupTableItem(
    item: PhaseLoadItem,
    decisionsByGroupNumber: Map<Int, List<DistributionDecision>>,
    expanded: Boolean,
    dragEnabled: Boolean,
    onToggle: () -> Unit,
    onRegisterDropZone: (Phase, Rect) -> Unit,
    onDragStartAttempt: (PhaseLoadContentKt_DragPayload, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEndAttempt: (PhaseLoadContentKt_DragPayload) -> Unit,
    isDropTargetHighlighted: Boolean,
    onDragCancel: () -> Unit,
    onDecisionDetailsClick: (groupNumber: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val compatibilityClick = onDecisionDetailsClick
    val phaseColor = VhColors.phase(item.phase.toUiPhase())
    val borderColor = if (isDropTargetHighlighted) phaseColor else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isDropTargetHighlighted) 2.dp else 1.dp

    Card(
        modifier = modifier.fillMaxWidth().animateContentSize()
            .border(borderWidth, borderColor, MaterialTheme.shapes.extraLarge)
            .onGloballyPositioned { onRegisterDropZone(item.phase, it.boundsInRoot()) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(if (isDropTargetHighlighted) 3.dp else 0.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(34.dp).background(phaseColor.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.phase.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = phaseColor
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${UiTextFormat.amperes(item.totalCurrent)} · ${UiTextFormat.power(item.totalPower)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${item.groups.size} ${plural(item.groups.size, "группа", "группы", "групп")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    item.groups.forEachIndexed { index, group ->
                        key(group.groupId) {
                            var handleCoords by remember(group.groupId) { mutableStateOf<LayoutCoordinates?>(null) }
                            var detailsExpanded by remember(group.groupId) { mutableStateOf(false) }
                            val payload = PhaseLoadContentKt_DragPayload(
                                groupId = group.groupId,
                                fromPhase = item.phase,
                                title = "Группа №${group.groupNumber} · ${group.roomName}"
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Группа ${group.groupNumber} · ${group.roomName}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = group.devices.joinToString { it.name }.ifBlank { "Без устройств" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (dragEnabled) {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier.onGloballyPositioned { handleCoords = it }
                                                .pointerInput(group.groupId) {
                                                    detectDragGestures(
                                                        onDragStart = { local ->
                                                            val coordinates = handleCoords ?: return@detectDragGestures
                                                            onDragStartAttempt(payload, coordinates.localToRoot(local))
                                                        },
                                                        onDrag = { change, _ ->
                                                            change.consume()
                                                            val coordinates = handleCoords ?: return@detectDragGestures
                                                            onDragMove(coordinates.localToRoot(change.position))
                                                        },
                                                        onDragCancel = onDragCancel,
                                                        onDragEnd = { onDragEndAttempt(payload) }
                                                    )
                                                }
                                        ) {
                                            Icon(Icons.Outlined.DragIndicator, contentDescription = "Перенести группу")
                                        }
                                    }
                                }

                                Text(
                                    text = "${UiTextFormat.power(group.totalPower)} · " +
                                        "расчётный ток ${UiTextFormat.amperes(group.totalCurrent)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                val decision = decisionsByGroupNumber[group.groupNumber].orEmpty().lastOrNull()
                                if (decision != null) {
                                    Text(
                                        text = if (detailsExpanded) "Скрыть расчёт" else "Как выбрана фаза",
                                        modifier = Modifier.clickable { detailsExpanded = !detailsExpanded },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    AnimatedVisibility(detailsExpanded) {
                                        val explanation = decision.toDecisionExplanationUi()
                                        Column(
                                            modifier = Modifier.padding(top = 5.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(explanation.levelA_title, style = MaterialTheme.typography.bodySmall)
                                            if (explanation.levelA_metric.isNotBlank()) {
                                                Text(explanation.levelA_metric, style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (explanation.levelB_reason.isNotBlank()) {
                                                Text(explanation.levelB_reason, style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text(explanation.levelB_before, style = MaterialTheme.typography.bodySmall)
                                            Text(explanation.levelB_after, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            if (index != item.groups.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

data class PhaseLoadContentKt_DragPayload(
    val groupId: Long,
    val fromPhase: Phase,
    val title: String
)

private fun plural(value: Int, one: String, few: String, many: String): String {
    val mod100 = value % 100
    val mod10 = value % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
