package ru.mugalimov.volthome.ui.screens.explication

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceChips(
    devices: List<Device>,
    onDeviceClick: (Long) -> Unit,

    /**
     * Старый контракт: long-press -> включает "move-mode" (панель целей).
     * Оставляем, чтобы не ломать текущий UI pipeline.
     */
    onDeviceLongPress: ((Long) -> Unit)? = null,

    /**
     * Новый контракт: drag-цепочка событий (manual-only).
     */
    onDeviceDragStart: ((deviceId: Long, itemStartRoot: Offset, pointerStartRoot: Offset) -> Unit)? = null,
    onDeviceDragMove: ((pointerRoot: Offset) -> Unit)? = null,
    onDeviceDragEnd: (() -> Unit)? = null,
    onDeviceDragCancel: (() -> Unit)? = null,

    enableLongPress: Boolean = false,
    maxVisible: Int = 3,
    groupKey: Any? = null,

    /**
     * Если true — первый видимый device chip становится anchor-целью
     * для подсказки про long-press.
     */
    anchorFirstVisibleDevice: Boolean = false
) {
    val rememberKey = groupKey ?: devices.joinToString("|") { it.id.toString() }
    val (expanded, setExpanded) = rememberSaveable(rememberKey) { mutableStateOf(false) }

    val total = devices.size
    val overflow = (total - maxVisible).coerceAtLeast(0)
    val visible = if (expanded || total <= maxVisible) devices else devices.take(maxVisible)

    FlowRow(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visible.forEachIndexed { index, d ->
            key(d.id) {
                val chipModifier = if (anchorFirstVisibleDevice && index == 0) {
                    Modifier.onboardingAnchor(
                        targetTag = OnboardingTargetTag.EXPLICATION_FIRST_DEVICE_CHIP,
                        screenId = OnboardingScreen.EXPLICATION
                    )
                } else {
                    Modifier
                }

                DeviceChip(
                    text = d.name,
                    onClick = {
                        onDeviceClick(d.id)
                    },

                    enableDrag = enableLongPress,
                    onDragStart = { itemStartRoot, pointerStartRoot ->
                        onDeviceLongPress?.invoke(d.id)
                        onDeviceDragStart?.invoke(d.id, itemStartRoot, pointerStartRoot)
                    },
                    onDragMove = { pointerRoot ->
                        onDeviceDragMove?.invoke(pointerRoot)
                    },
                    onDragEnd = {
                        onDeviceDragEnd?.invoke()
                    },
                    onDragCancel = {
                        onDeviceDragCancel?.invoke()
                    },
                    modifier = chipModifier
                )
            }
        }

        if (!expanded && overflow > 0) {
            DeviceChip(
                text = "+$overflow",
                onClick = { setExpanded(true) },
                enableDrag = false,
                onDragStart = null,
                onDragMove = null,
                onDragEnd = null,
                onDragCancel = null
            )
        }

        if (expanded && overflow > 0) {
            DeviceChip(
                text = "Свернуть",
                onClick = { setExpanded(false) },
                enableDrag = false,
                onDragStart = null,
                onDragMove = null,
                onDragEnd = null,
                onDragCancel = null
            )
        }
    }
}

@Composable
private fun DeviceChip(
    text: String,
    onClick: () -> Unit,

    // Drag контракт (manual-only)
    enableDrag: Boolean,
    onDragStart: ((itemStartRoot: Offset, pointerStartRoot: Offset) -> Unit)?,
    onDragMove: ((pointerRoot: Offset) -> Unit)?,
    onDragEnd: (() -> Unit)?,
    onDragCancel: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    // LayoutCoordinates нельзя хранить через rememberSaveable.
    val coordsState = remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Если end уже произошёл — cancel игнорируем.
    val dragEndedOrCanceled = remember { mutableStateOf(false) }

    val dragModifier = if (enableDrag) {
        Modifier
            .onGloballyPositioned { coords ->
                coordsState.value = coords
            }
            .pointerInput(Unit) {
                dragEndedOrCanceled.value = false

                detectDragGesturesAfterLongPress(
                    onDragStart = { startLocal ->
                        dragEndedOrCanceled.value = false

                        val coords = coordsState.value ?: return@detectDragGesturesAfterLongPress
                        val itemStartRoot = coords.positionInRoot()
                        val pointerStartRoot = coords.localToRoot(startLocal)
                        onDragStart?.invoke(itemStartRoot, pointerStartRoot)
                    },
                    onDrag = { change, _ ->
                        if (dragEndedOrCanceled.value) return@detectDragGesturesAfterLongPress

                        val coords = coordsState.value ?: return@detectDragGesturesAfterLongPress
                        val pointerRoot = coords.localToRoot(change.position)
                        onDragMove?.invoke(pointerRoot)
                        change.consume()
                    },
                    onDragEnd = {
                        if (dragEndedOrCanceled.value) return@detectDragGesturesAfterLongPress
                        dragEndedOrCanceled.value = true

                        Log.d("DRAG_TRACE", "UI onDragEnd text=$text")
                        onDragEnd?.invoke()
                    },
                    onDragCancel = {
                        if (dragEndedOrCanceled.value) {
                            Log.d("DRAG_TRACE", "UI onDragCancel IGNORED (already ended) text=$text")
                            return@detectDragGesturesAfterLongPress
                        }
                        dragEndedOrCanceled.value = true

                        Log.d("DRAG_TRACE", "UI onDragCancel text=$text")
                        onDragCancel?.invoke()
                    }
                )
            }
    } else {
        Modifier
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.60f)),
        modifier = modifier
            .then(dragModifier)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                // Обычный тап всегда работает.
                onClick = onClick,
                // Long click здесь не используем:
                // long-press управляется drag-детектором.
                onLongClick = null
            )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}