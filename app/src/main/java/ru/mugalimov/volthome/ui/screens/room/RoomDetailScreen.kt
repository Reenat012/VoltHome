package ru.mugalimov.volthome.ui.screens.room

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.ui.sheets.DevicePickerSheet
import ru.mugalimov.volthome.ui.components.VhTopBar
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    onBack: () -> Unit,
    vm: RoomDetailViewModel = hiltViewModel()
) {
    val room: Room? by vm.room.collectAsState()
    val uiState by vm.uiState.collectAsState()
    val defaultDevices by vm.defaultDevices.collectAsState()

    var showAllDevices by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            VhTopBar(
                title = room?.name ?: "Комната",
                subtitle = roomTypeLabel(room?.roomType ?: RoomType.STANDARD),
                onBack = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAllDevices = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Добавить устройства") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            RoomSummary(
                count = uiState.devices.size,
                totalPowerW = uiState.devices.sumOf { it.power },
                calculatedPowerW = uiState.devices.sumOf { it.power * it.demandRatio },
                dedicatedCount = uiState.devices.count { it.requiresDedicatedCircuit },
                roomType = room?.roomType ?: RoomType.STANDARD
            )
            Spacer(Modifier.height(8.dp))
            DeviceListEditable(
                devices = uiState.devices,
                modifier = Modifier.fillMaxSize(),
                onDelete = { id -> vm.deleteDevice(id) }
            )
        }
    }

    if (showAllDevices) {
        DevicePickerSheet(
            roomId = room?.id ?: 0L,
            defaultDevices = defaultDevices,
            onDismiss = { showAllDevices = false },
            onAdded = { _ ->
                showAllDevices = false
            }
        )
    }
}

/* ---------- Room summary: нейтральная панель статистики ---------- */

@Composable
private fun RoomSummary(
    count: Int,
    totalPowerW: Int,
    calculatedPowerW: Double,
    dedicatedCount: Int,
    roomType: RoomType
) {
    val demandShare = if (totalPowerW > 0) (calculatedPowerW / totalPowerW).coerceIn(0.0, 1.0) else 0.0
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Сводка помещения", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (roomType == RoomType.STANDARD) "Обычные условия" else "Особая зона · защита учитывается",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatBadge(text = "$count ${deviceWord(count)}")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetric(
                    label = "Установлено",
                    value = "${(totalPowerW / 1000.0).format(2)}\u00A0кВт",
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = "Расчётная нагрузка",
                    value = "${(calculatedPowerW / 1000.0).format(2)}\u00A0кВт",
                    modifier = Modifier.weight(1f)
                )
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { demandShare.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
            Text(
                text = if (dedicatedCount > 0) "$dedicatedCount ${if (dedicatedCount == 1) "выделенная линия" else "выделенные линии"}"
                else "Выделенные линии не требуются",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun deviceWord(value: Int): String {
    val mod100 = value % 100
    val mod10 = value % 10
    return when {
        mod100 in 11..14 -> "устройств"
        mod10 == 1 -> "устройство"
        mod10 in 2..4 -> "устройства"
        else -> "устройств"
    }
}

@Composable
private fun StatBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/* ---------- UI helpers (семантика RoomTypeAvatar сохранена) ---------- */

@Composable
private fun RoomTypeAvatar(type: RoomType, modifier: Modifier = Modifier) {
    val bg = when (type) {
        RoomType.STANDARD -> MaterialTheme.colorScheme.primaryContainer
        RoomType.BATHROOM -> MaterialTheme.colorScheme.tertiaryContainer
        RoomType.KITCHEN -> MaterialTheme.colorScheme.secondaryContainer
        RoomType.OUTDOOR -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (type) {
        RoomType.STANDARD -> MaterialTheme.colorScheme.onPrimaryContainer
        RoomType.BATHROOM -> MaterialTheme.colorScheme.onTertiaryContainer
        RoomType.KITCHEN -> MaterialTheme.colorScheme.onSecondaryContainer
        RoomType.OUTDOOR -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (type) {
        RoomType.STANDARD -> Icons.Rounded.Home
        RoomType.BATHROOM -> Icons.Rounded.Bathtub
        RoomType.KITCHEN -> Icons.Rounded.Kitchen
        RoomType.OUTDOOR -> Icons.Rounded.Park
    }
    Surface(color = bg, contentColor = fg, modifier = modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null)
        }
    }
}

private fun Double.format(d: Int) = "%.${d}f".format(this).replace('.', ',')
private fun roomTypeLabel(type: RoomType): String = when (type) {
    RoomType.STANDARD -> "Стандартная"
    RoomType.BATHROOM -> "Ванная"
    RoomType.KITCHEN -> "Кухня"
    RoomType.OUTDOOR -> "Улица"
}
