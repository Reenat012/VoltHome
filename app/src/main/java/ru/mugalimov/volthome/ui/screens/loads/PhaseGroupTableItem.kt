package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onDragEnd: (payload: PhaseLoadContentKt_DragPayload) -> Unit
) {
    // ⚠️ Хак: нельзя сослаться на локальный data class из другого файла напрямую.
    // Поэтому ниже я использую "typealias" через внутреннюю обёртку:
    // смотри внизу файла PhaseLoadContent.kt — там должен быть typealias.
    // Если не хочешь typealias — скажи, дам вариант с отдельным файлом модели.

    val phaseAccent = when (item.phase) {
        Phase.A -> Color(0xFFF6D96B).copy(alpha = 0.25f)
        Phase.B -> Color(0xFF7ED492).copy(alpha = 0.25f)
        Phase.C -> Color(0xFFFF8A80).copy(alpha = 0.25f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .onGloballyPositioned { coords ->
                onRegisterDropZone(item.phase, coords.boundsInRoot())
            },
        colors = CardDefaults.cardColors(containerColor = phaseAccent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                        var groupCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                        Box(
                            modifier = Modifier
                                .onGloballyPositioned { groupCoords = it }
                                .pointerInput(canDrag, group.groupId) {
                                    detectDragGestures(
                                        onDragStart = {
                                            if (!canDrag) {
                                                onPaywall()
                                                return@detectDragGestures
                                            }
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
                                            change.consume()

                                            val c = groupCoords ?: return@detectDragGestures
                                            // позиция в координатах root
                                            val root = c.localToRoot(change.position)
                                            onDragMove(root)
                                        },
                                        onDragCancel = {
                                            // просто сбросим drag-состояние через onDragEnd с тем же payload нельзя,
                                            // поэтому ничего — overlay исчезнет из-за отмены в parent по onDragStart/onDragEnd логике
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
                                            label = { Text(deviceName.name) },
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

/**
 * ✅ Чтобы не городить отдельный файл модели, выносим payload как публичный data class.
 * Тогда PhaseLoadContent и PhaseGroupTableItem используют один и тот же тип.
 */
data class PhaseLoadContentKt_DragPayload(
    val groupId: Long,
    val fromPhase: Phase,
    val title: String
)