package ru.mugalimov.volthome.ui.screens.explication

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceChips(
    devices: List<Device>,
    onDeviceClick: (Long) -> Unit,

    /**
     * Старый контракт: long-press -> включает "move-mode" (панель целей).
     * В этом коммите оставляем, чтобы не менять текущий UI.
     */
    onDeviceLongPress: ((Long) -> Unit)? = null,

    /**
     * Новый контракт: drag-цепочка событий (manual-only).
     * В этом коммите UI ghost ещё нет — только VM события.
     */
    onDeviceDragStart: ((deviceId: Long, itemStartRoot: Offset, pointerStartRoot: Offset) -> Unit)? = null,
    onDeviceDragMove: ((pointerRoot: Offset) -> Unit)? = null,
    onDeviceDragEnd: (() -> Unit)? = null,
    onDeviceDragCancel: (() -> Unit)? = null,

    enableLongPress: Boolean = false,
    maxVisible: Int = 3,
    groupKey: Any? = null // привязываем state раскрытия к группе
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
        visible.forEach { d ->
            key(d.id) {
                DeviceChip(
                    text = d.name,
                    onClick = { onDeviceClick(d.id) },

                    // ✅ В manual включаем drag по long-press (через detectDragGesturesAfterLongPress)
                    enableDrag = enableLongPress,
                    onDragStart = { itemStartRoot, pointerStartRoot ->
                        // ВАЖНО: сохраняем старое поведение (панель целей),
                        // чтобы UI в этом коммите не поменялся.
                        onDeviceLongPress?.invoke(d.id)

                        // Новый контракт: startDrag в VM
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
                    }
                )
            }
        }

        if (!expanded && overflow > 0) {
            // Кнопка раскрытия — обычный клик, long-press/drag не нужен
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
    onDragCancel: (() -> Unit)?
) {
    val cs = MaterialTheme.colorScheme

    // Координаты чипа в root-системе — нужны для вычисления itemStartRoot/pointerRoot.
// ВАЖНО: LayoutCoordinates нельзя сохранять через rememberSaveable (не Bundle-тип).
    val coordsState = remember { mutableStateOf<LayoutCoordinates?>(null) }

    val dragModifier = if (enableDrag) {
        Modifier
            // Снимаем геометрию чипа в root координатах.
            .onGloballyPositioned { coordsState.value = it }
            // Long-press -> drag. ВАЖНО: click остаётся через combinedClickable ниже.
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startLocal ->
                        val coords = coordsState.value ?: return@detectDragGesturesAfterLongPress
                        val itemStartRoot = coords.positionInRoot()
                        val pointerStartRoot = coords.localToRoot(startLocal)
                        onDragStart?.invoke(itemStartRoot, pointerStartRoot)
                    },
                    onDrag = { change, _ ->
                        // В drag нам важна позиция пальца в root координатах
                        val coords = coordsState.value ?: return@detectDragGesturesAfterLongPress
                        val pointerRoot = coords.localToRoot(change.position)
                        onDragMove?.invoke(pointerRoot)

                        // Потребляем изменение, чтобы drag не дрался со scroll после long-press
                        change.consume()
                    },
                    onDragEnd = {
                        onDragEnd?.invoke()
                    },
                    onDragCancel = {
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
        modifier = Modifier
            .then(dragModifier)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                // ✅ Tap всегда работает (когда drag не активен)
                onClick = onClick,
                // ВАЖНО: onLongClick тут НЕ используем — long-press отрабатывает drag-детектор.
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