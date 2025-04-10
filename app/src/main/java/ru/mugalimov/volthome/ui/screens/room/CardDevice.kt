package ru.mugalimov.volthome.ui.screens.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel

//карточка устройства в списке
@Composable
fun CardDevice(
    device: Device,
//    onClickDevice: () -> Unit,
    onDelete: () -> Unit,
    viewModel: RoomDetailViewModel = hiltViewModel<RoomDetailViewModel>()
) {
    // Состояния из viewModel
    val devicesDefault by viewModel.defaultDevices.collectAsState()

    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
//            .clickable { onClickDevice() } // Обработчик клика на карточку
    ) {
        Row(modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                .weight(1f) // Занимает всё свободное пространство
                    .padding(16.dp),
//            verticalAlignment = Alignment.CenterVertically // Выравнивание по вертикали
            ) {
                Text(
                    text = device.name,
//                modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = device.power.toString(),
//                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = device.voltage.toString(),
//                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = device.demandRatio.toString(),
//                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
            }

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