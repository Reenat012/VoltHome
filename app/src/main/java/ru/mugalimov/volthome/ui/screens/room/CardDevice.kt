package ru.mugalimov.volthome.ui.screens.room

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.tensorflow.lite.support.label.Category
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel

@Composable
fun CardDevice(
    device: Device,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: RoomDetailViewModel = hiltViewModel()
    val devicesDefault by viewModel.defaultDevices.collectAsState()

    Card(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceInfoRow(
                    icon = Icons.Outlined.ElectricalServices,
                    label = "Устройство",
                    value = device.name
                )

                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp
                )

                DeviceParameterRow(
                    label = "Мощность",
                    value = "${device.power} Вт",
                    icon = Icons.Outlined.Bolt
                )

                DeviceParameterRow(
                    label = "Напряжение",
                    value = "${device.voltage.value} В",
                    icon = Icons.Outlined.FlashOn
                )

                DeviceParameterRow(
                    label = "Коэф. спроса",
                    value = "%.2f".format(device.demandRatio),
                    icon = Icons.Outlined.Calculate
                )

                DeviceParameterRow(
                    label = "Коэф. мощности",
                    value = "%.2f".format(device.powerFactor),
                    icon = Icons.Outlined.Emergency
                )

                DeviceParameterRow(
                    label = "Ток",
                    value = "%.2f А".format(device.current),
                    icon = Icons.Outlined.ElectricBolt
                )

                DeviceParameterRow(
                    label = "Группа",
                    value = device.deviceType.toString(),
                    icon = Icons.Outlined.Category
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Удалить устройство"
                )
            }
        }
    }
}

@Composable
private fun DeviceParameterRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "$label иконка", // Исправлено описание
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        ) // Добавлена закрывающая скобка

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f) // Добавлен вес для адаптивности
        )
    }
}

@Composable
private fun DeviceInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "$label иконка", // Уточненное описание
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}