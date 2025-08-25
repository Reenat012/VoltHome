package ru.mugalimov.volthome.ui.screens.room


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Device

@Composable
fun DeviceList(
    devices: List<Device>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp
    )
) {
    LazyColumn(
        modifier = modifier,                          // никакого Surface/Card тут
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = contentPadding
    ) {
        items(devices, key = { it.id }) { device ->
            // ВАЖНО: Никаких обёрток вокруг CardDevice
            CardDevice(device = device)
        }
    }
}