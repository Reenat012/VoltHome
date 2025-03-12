package ru.mugalimov.volthome.ui.screens.room

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.mugalimov.volthome.model.Device

//Список устройств
@Composable
fun DeviceList(
    devices: List<Device>,
    onClickDevice: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(devices, key = { it.id }) { device ->
            CardDevice (
                device = device,
                onDelete = { onDelete(device.id) },
                onClickDevice = { onClickDevice(device.id) } // Передаем ID устройства
            )
        }
    }
}