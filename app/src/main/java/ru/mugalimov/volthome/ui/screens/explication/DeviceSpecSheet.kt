package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceSpecUi
import ru.mugalimov.volthome.domain.model.Voltage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSpecSheet(
    device: DeviceSpecUi,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(device.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))

            // Технические параметры
            SpecRow(Icons.Outlined.Power,     "Мощность",               "${device.power} Вт")
            SpecRow(Icons.Outlined.Bolt,      "Напряжение",             device.voltage?.let { "$it В" } ?: "—")
            SpecRow(Icons.Outlined.Calculate, "Коэффициент спроса",     device.demandRatio?.toString() ?: "—")
            SpecRow(Icons.Outlined.Emergency, "Коэффициент мощности",   device.powerFactor?.toString() ?: "—")

            // Тип/логика подключения — добавили иконки
            SpecRow(Icons.Outlined.Devices,   "Тип устройства",         device.deviceType ?: "—")
            SpecRow(Icons.Outlined.Cable,     "Выделенная линия",       if (device.requiresDedicatedCircuit) "Да" else "Нет")
            SpecRow(Icons.Outlined.ElectricalServices,"Требует точку подключения/розетку",       device.requiresSocketConnection?.let { if (it) "Да" else "Нет" } ?: "—")
            // Для «Двигатель» используем ту же строку, чтобы стиль был единый
            SpecRow(Icons.Outlined.Build,   "Двигатель",              if (device.hasMotor) "Есть" else "Нет")

            Spacer(Modifier.height(16.dp))
        }
    }
}

// Единый ряд «иконка — лейбл — значение»
@Composable
private fun SpecRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SpecRowText(label: String, value: String) {
    RowLabelValue(null, label, value)
}

@Composable
private fun RowLabelValue(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    value: String
) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}