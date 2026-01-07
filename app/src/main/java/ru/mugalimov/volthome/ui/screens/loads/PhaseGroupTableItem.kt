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
import androidx.compose.foundation.layout.Box
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
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

@Composable
fun PhaseGroupTableItem(
    item: PhaseLoadItem,
    expanded: Boolean,
    onToggle: () -> Unit,

    canDrag: Boolean,
    onPaywall: () -> Unit,

    onRegisterDropZone: (Phase, Rect) -> Unit,

    onDragStart: (payload: PhaseLoadContentKt_DragPayload) -> Unit,
    onDragMove: (rootPos: Offset) -> Unit,
    onDragEnd: (payload: PhaseLoadContentKt_DragPayload) -> Unit,
    isDropTargetHighlighted: Boolean,
    onDragCancel: () -> Unit,
) {
    val uiPhase = item.phase.toUiPhase()
    val phaseAccent = VhColors.phase(uiPhase).copy(alpha = 0.25f)

    val highlightAlpha = if (isDropTargetHighlighted) 0.55f else 0.25f
    val borderColor = if (isDropTargetHighlighted)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

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

            // Заголовок секции фазы (без drag)
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
                        var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                        // ВАЖНО: pointerInput теперь ТОЛЬКО на handle (явное действие).
                        // Тело блока не перехватывает жест → scroll работает по умолчанию.
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
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

                                    IconButton(
                                        onClick = {
                                            // onClick не используется — drag идёт через жест.
                                            // Нажатие на кнопку без жеста ничего не делает.
                                        },
                                        modifier = Modifier
                                            .onGloballyPositioned { handleCoords = it }
                                            .pointerInput(canDrag, group.groupId) {
                                                detectDragGestures(
                                                    onDragStart = { startLocal ->
                                                        if (!canDrag) {
                                                            onPaywall()
                                                            return@detectDragGestures
                                                        }

                                                        val c = handleCoords
                                                            ?: return@detectDragGestures
                                                        val startRoot = c.localToRoot(startLocal)

                                                        // Чтобы overlay не “прыгал” с (0,0)
                                                        onDragMove(startRoot)

                                                        onDragStart(
                                                            PhaseLoadContentKt_DragPayload(
                                                                groupId = group.groupId,
                                                                fromPhase = item.phase,
                                                                title = "Группа №${group.groupNumber} (${group.roomName})"
                                                            )
                                                        )
                                                    },
                                                    onDrag = { change, _ ->
                                                        if (!canDrag) return@detectDragGestures
                                                        // Здесь намеренно consume — это уже явный drag.
                                                        change.consume()
                                                        val c = handleCoords
                                                            ?: return@detectDragGestures
                                                        onDragMove(c.localToRoot(change.position))
                                                    },
                                                    onDragCancel = {
                                                        onDragCancel()
                                                    },
                                                    onDragEnd = {
                                                        if (!canDrag) return@detectDragGestures
                                                        onDragEnd(
                                                            PhaseLoadContentKt_DragPayload(
                                                                groupId = group.groupId,
                                                                fromPhase = item.phase,
                                                                title = "Группа №${group.groupNumber} (${group.roomName})"
                                                            )
                                                        )
                                                    }
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

/**
 * Общий payload для DnD.
 */
data class PhaseLoadContentKt_DragPayload(
    val groupId: Long,
    val fromPhase: Phase,
    val title: String
)