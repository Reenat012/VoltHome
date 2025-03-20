package ru.mugalimov.volthome.ui.screens.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.model.Device

//карточка устройства в списке
@Composable
fun CardDevice(
    device: Device,
//    onClickDevice: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
//            .clickable { onClickDevice() } // Обработчик клика на карточку
    ) {
        Row (
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = device.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = device.power.toString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = device.voltage.toString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = device.demandRatio.toString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}