package ru.mugalimov.volthome.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.ui.sheets.DeviceEditSheet

/**
 * Обёртка над существующими карточками устройства:
 * добавляет FAB-карандаш на карточку и открывает шит редактирования.
 */
@Composable
fun DeviceListEditable(
    devices: List<Device>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    onDelete: (Long) -> Unit
) {
    val editingId = remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = contentPadding
    ) {
        items(devices, key = { it.id }) { device ->
            Box {
                // Используем твою существующую карточку:
                CardDevice(device = device, onDeleteClick = onDelete)

                FloatingActionButton(
                    onClick = { editingId.value = device.id },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp)
                        .size(36.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Изменить")
                }
            }
        }
    }

    editingId.value?.let { id ->
        DeviceEditSheet(
            deviceId = id,
            onDismiss = { editingId.value = null },
            onSaved = { editingId.value = null }
        )
    }
}