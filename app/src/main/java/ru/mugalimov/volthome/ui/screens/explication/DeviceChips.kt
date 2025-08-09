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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceChips(
    devices: List<String>,
    onDeviceClick: (String) -> Unit = {}
) {
    FlowRow(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        devices.take(3).forEach { name ->
            AssistChip(
                onClick = { onDeviceClick(name) },
                label = { Text(name, style = MaterialTheme.typography.labelLarge) },
                colors = AssistChipDefaults.assistChipColors()
            )
        }
        if (devices.size > 3) {
            AssistChip(onClick = { /* можно открыть полный список */ }, label = { Text("+${devices.size - 3}") })
        }
    }
}