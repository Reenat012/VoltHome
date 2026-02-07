package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Device

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceChips(
    devices: List<Device>,
    onDeviceClick: (Long) -> Unit,
    onDeviceLongPress: ((Long) -> Unit)? = null,
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
                AssistChip(
                    onClick = { onDeviceClick(d.id) },
                    modifier = Modifier.combinedClickable(
                        onClick = { onDeviceClick(d.id) },
                        onLongClick = {
                            if (enableLongPress) onDeviceLongPress?.invoke(d.id)
                        }
                    ),
                    label = { Text(d.name, style = MaterialTheme.typography.labelLarge) },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }
        }

        if (!expanded && overflow > 0) {
            AssistChip(
                onClick = { setExpanded(true) },
                label = { Text("+$overflow") },
                colors = AssistChipDefaults.assistChipColors()
            )
        }

        if (expanded && overflow > 0) {
            AssistChip(
                onClick = { setExpanded(false) },
                label = { Text("Свернуть") },
                colors = AssistChipDefaults.assistChipColors()
            )
        }
    }
}