package ru.mugalimov.volthome.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Lightweight tooltip:
 * - открывается по клику на ⓘ (Icons.Outlined.Info)
 * - закрывается по тапу вне области (Popup focusable + onDismissRequest)
 * - без DataStore, без rememberSaveable, без флагов показа
 */
@Composable
fun InfoTooltip(
    contentDescription: String = "Info",
    tooltip: @Composable () -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    InfoTooltip(expanded = expanded, contentDescription = contentDescription, tooltip = tooltip)
}

@Composable
fun InfoTooltip(
    expanded: MutableState<Boolean>,
    contentDescription: String = "Info",
    tooltip: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var anchorOffset = remember { IntOffset(0, 0) }
    var anchorHeightPx = remember { 0 }

    IconButton(
        onClick = { expanded.value = true },
        modifier = Modifier.onGloballyPositioned { coords ->
            val pos = coords.positionInWindow()
            anchorOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
            anchorHeightPx = coords.size.height
        }
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (expanded.value) {
        val y = anchorOffset.y + anchorHeightPx + with(density) { 6.dp.toPx() }.toInt()
        Popup(
            offset = IntOffset(anchorOffset.x, y),
            properties = PopupProperties(focusable = true),
            onDismissRequest = { expanded.value = false }
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    tooltip()
                }
            }
        }
    }
}