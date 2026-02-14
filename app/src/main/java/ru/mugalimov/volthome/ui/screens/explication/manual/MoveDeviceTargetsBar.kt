package ru.mugalimov.volthome.ui.screens.explication.manual

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import kotlin.math.abs

@Composable
fun MoveDeviceTargetsBar(
    title: String,
    fromGroupId: Long,
    groups: List<ManualGroupDraft>,
    onDismiss: () -> Unit,
    onMoveToGroup: (targetGroupId: Long) -> Unit,
    onMoveToNewGroup: () -> Unit,
    onMoveToUnassigned: () -> Unit,
    activeTarget: ExplicationViewModel.DragTarget?,
    onTargetBounds: (target: ExplicationViewModel.DragTarget, boundsInRoot: Rect) -> Unit,

    // ✅ Текущая позиция пальца + флаг drag
    pointerRoot: Offset?,
    isDragging: Boolean,

    modifier: Modifier = Modifier
) {
    SideEffect {
        Log.d(
            "DRAG_UI",
            "TargetsBar recompose activeTarget=$activeTarget fromGroupId=$fromGroupId isDragging=$isDragging pointer=$pointerRoot"
        )
    }

    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()

    // bounds бара (в root)
    var barBounds by remember { mutableStateOf<Rect?>(null) }

    // свежие значения для корутины
    val pointerLatest = rememberUpdatedState(pointerRoot)
    val boundsLatest = rememberUpdatedState(barBounds)
    val draggingLatest = rememberUpdatedState(isDragging)

    /**
     * ✅ Edge-autoscroll без ANR:
     * - НИКАКИХ busy-loop/yield
     * - максимум 1 раз на кадр через withFrameNanos
     */
    androidx.compose.runtime.LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect

        // Чувствительность:
        val edgePx = 48f     // зона у края
        val maxSpeedPx = 28f // px/кадр (мягко, но заметно)

        while (draggingLatest.value) {
            // ждём следующий кадр -> не блокируем обработку input
            withFrameNanos { /* frame tick */ }

            val p = pointerLatest.value
            val b = boundsLatest.value
            if (p == null || b == null) continue

            val left = b.left
            val right = b.right

            val distToLeft = p.x - left
            val distToRight = right - p.x

            val inLeft = distToLeft in 0f..edgePx
            val inRight = distToRight in 0f..edgePx

            val scrollDelta = when {
                inLeft -> {
                    val k = (edgePx - distToLeft) / edgePx // 0..1
                    -maxSpeedPx * k
                }
                inRight -> {
                    val k = (edgePx - distToRight) / edgePx // 0..1
                    +maxSpeedPx * k
                }
                else -> 0f
            }

            if (abs(scrollDelta) >= 0.5f) {
                listState.scrollBy(scrollDelta)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                barBounds = coords.boundsInRoot()
                Log.d("DRAG_BOUNDS", "BAR boundsRoot=$barBounds")
            },
        color = cs.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
                }
            }

            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    items = groups.sortedBy { it.groupNumber },
                    key = { it.groupId }
                ) { g ->
                    val disabled = g.groupId == fromGroupId
                    val target = ExplicationViewModel.DragTarget.Group(g.groupId)
                    val isActive = activeTarget == target

                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val r = coords.boundsInRoot()
                            Log.d("DRAG_BOUNDS", "BAR group target=$target rectRoot=$r disabled=$disabled isActive=$isActive")
                            onTargetBounds(target, r)
                        }
                    ) {
                        AssistChip(
                            onClick = { if (!disabled) onMoveToGroup(g.groupId) },
                            enabled = !disabled,
                            label = { Text("Группа ${g.groupNumber}") },
                            border = when {
                                isActive -> BorderStroke(2.dp, cs.secondary)
                                else -> AssistChipDefaults.assistChipBorder(
                                    enabled = !disabled,
                                    borderColor = cs.outlineVariant
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isActive) cs.secondaryContainer else cs.surface,
                                labelColor = if (isActive) cs.onSecondaryContainer else cs.onSurface,
                                disabledContainerColor = cs.surfaceVariant,
                                disabledLabelColor = cs.onSurfaceVariant
                            )
                        )
                    }
                }

                item {
                    val target = ExplicationViewModel.DragTarget.NewGroup
                    val isActive = activeTarget == target

                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val r = coords.boundsInRoot()
                            Log.d("DRAG_BOUNDS", "BAR newGroup target=$target rectRoot=$r isActive=$isActive")
                            onTargetBounds(target, r)
                        }
                    ) {
                        AssistChip(
                            onClick = onMoveToNewGroup,
                            label = { Text("В новую группу") },
                            border = if (isActive) BorderStroke(2.dp, cs.secondary) else null,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isActive) cs.secondaryContainer else cs.surface,
                                labelColor = if (isActive) cs.onSecondaryContainer else cs.onSurface
                            )
                        )
                    }
                }

                item {
                    val target = ExplicationViewModel.DragTarget.Unassigned
                    val isActive = activeTarget == target

                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val r = coords.boundsInRoot()
                            Log.d("DRAG_BOUNDS", "BAR unassigned target=$target rectRoot=$r isActive=$isActive")
                            onTargetBounds(target, r)
                        }
                    ) {
                        AssistChip(
                            onClick = onMoveToUnassigned,
                            label = { Text("В нераспределённые") },
                            border = if (isActive) BorderStroke(2.dp, cs.secondary) else null,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isActive) cs.secondaryContainer else cs.surface,
                                labelColor = if (isActive) cs.onSecondaryContainer else cs.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}