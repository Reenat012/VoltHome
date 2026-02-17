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
import androidx.compose.ui.layout.positionInWindow
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

    val TAG_CHIPS = "EXP_CHIPS"

    val total = devices.size
    val overflow = (total - maxVisible).coerceAtLeast(0)
    val visible = if (expanded || total <= maxVisible) devices else devices.take(maxVisible)

//    Log.d(
//        TAG_CHIPS,
//        "render groupKey=$groupKey rememberKey=$rememberKey total=$total visible=${visible.size} " +
//                "overflow=$overflow expanded=$expanded enableLongPress=$enableLongPress"
//    )

    FlowRow(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visible.forEach { d ->
            key(d.id) {
                DeviceChip(
                    text = d.name,
                    onClick = {
//                        Log.d(TAG_CHIPS, "click deviceId=${d.id} name=${d.name}")
                        onDeviceClick(d.id)
                    },

                    enableDrag = enableLongPress,
                    onDragStart = { itemStartRoot, pointerStartRoot ->
//                        Log.d(
//                            TAG_CHIPS,
//                            "dragStart deviceId=${d.id} name=${d.name} itemStartRoot=$itemStartRoot pointerStartRoot=$pointerStartRoot"
//                        )

                        // ВАЖНО: это старое поведение — включение панели целей
                        onDeviceLongPress?.invoke(d.id)

                        // Новый контракт: startDrag в VM
                        onDeviceDragStart?.invoke(d.id, itemStartRoot, pointerStartRoot)
                    },
                    onDragMove = { pointerRoot ->
//                        Log.v(TAG_CHIPS, "dragMove deviceId=${d.id} name=${d.name} pointerRoot=$pointerRoot")
                        onDeviceDragMove?.invoke(pointerRoot)
                    },
                    onDragEnd = {
//                        Log.d(TAG_CHIPS, "dragEnd deviceId=${d.id} name=${d.name}")
                        onDeviceDragEnd?.invoke()
                    },
                    onDragCancel = {
//                        Log.w(TAG_CHIPS, "dragCancel deviceId=${d.id} name=${d.name}")
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
//    val TAG_CHIP = "EXP_CHIP"
    val coordsState = remember { mutableStateOf<LayoutCoordinates?>(null) }

    val dragModifier = if (enableDrag) {
        Modifier
            .onGloballyPositioned { coords ->
                coordsState.value = coords
//                Log.v(TAG_CHIP, "positioned text=$text root=${coords.positionInRoot()}")
            }
            .pointerInput(Unit) {
//                Log.d(TAG_CHIP, "pointerInput ACTIVE text=$text enableDrag=$enableDrag")

                detectDragGesturesAfterLongPress(
                    onDragStart = { startLocal ->
                        val coords = coordsState.value
                        if (coords == null) {
//                            Log.e(TAG_CHIP, "dragStart SKIP coords=null text=$text")
                            return@detectDragGesturesAfterLongPress
                        }

                        val itemStartRoot = coords.positionInRoot()
                        val pointerStartRoot = coords.localToRoot(startLocal)

//                        Log.d(TAG_CHIP, "DRAG_START text=$text itemStartRoot=$itemStartRoot pointerStartRoot=$pointerStartRoot")
                        onDragStart?.invoke(itemStartRoot, pointerStartRoot)
                    },
                    onDrag = { change, _ ->
                        val coords = coordsState.value
                        if (coords == null) {
//                            Log.e(TAG_CHIP, "dragMove SKIP coords=null text=$text")
                            return@detectDragGesturesAfterLongPress
                        }

                        val pointerRoot = coords.localToRoot(change.position)
//                        Log.v(TAG_CHIP, "DRAG_MOVE text=$text pointerRoot=$pointerRoot consumed=${change.isConsumed}")

                        onDragMove?.invoke(pointerRoot)
                        change.consume()
                    },
                    onDragEnd = {
//                        Log.d(TAG_CHIP, "DRAG_END text=$text")
                        Log.d("DRAG_TRACE", "UI onDragEnd text=$text")
                        onDragEnd?.invoke()
                    },
                    onDragCancel = {
//                        Log.w(TAG_CHIP, "DRAG_CANCEL text=$text")
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