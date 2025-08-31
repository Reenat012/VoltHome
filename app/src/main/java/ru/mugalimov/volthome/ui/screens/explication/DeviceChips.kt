package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceChips(
    devices: List<String>,
    onDeviceClick: (String) -> Unit = {},
    maxVisible: Int = 3,
    groupKey: Any? = null // передавай, например, group.groupNumber
) {
    val rememberKey = groupKey ?: devices.joinToString("|")
    val (expanded, setExpanded) = rememberSaveable(rememberKey) { mutableStateOf(false) }

    val total = devices.size
    val overflow = (total - maxVisible).coerceAtLeast(0)
    val visible = if (expanded || total <= maxVisible) devices else devices.take(maxVisible)

    FlowRow(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // чипы устройств
        visible.forEach { name ->
            AssistChip(
                onClick = { onDeviceClick(name) },
                label = { Text(name, style = MaterialTheme.typography.labelLarge) },
                colors = AssistChipDefaults.assistChipColors()
            )
        }

        // «+N» для раскрытия
        if (!expanded && overflow > 0) {
            AssistChip(
                onClick = { setExpanded(true) },
                label = { Text("+$overflow") },
                colors = AssistChipDefaults.assistChipColors()
            )
        }

        // «Свернуть» когда раскрыто
        if (expanded && overflow > 0) {
            AssistChip(
                onClick = { setExpanded(false) },
                label = { Text("Свернуть") },
                colors = AssistChipDefaults.assistChipColors()
            )
        }
    }
}