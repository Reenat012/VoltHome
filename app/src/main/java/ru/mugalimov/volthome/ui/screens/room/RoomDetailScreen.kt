package ru.mugalimov.volthome.ui.screens.room

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
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoomTypeAvatar(
                            type = room?.roomType ?: RoomType.STANDARD,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = room?.name ?: "Комната",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Тип комнаты: ${roomTypeLabel(room?.roomType ?: RoomType.STANDARD)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
                totalPowerW = uiState.devices.sumOf { it.power }
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
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
                // при необходимости: показать snackbar/проскроллить к новым
                showAllDevices = false
            }
        )
    }
}

/* ---------- Room summary: акцентные статистические бейджи ---------- */

@Composable
private fun RoomSummary(count: Int, totalPowerW: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
        ),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatBadge(text = "$count устройств(а)")
            StatBadge(text = "${(totalPowerW / 1000.0).format(2)} кВт")
        }
    }
}

@Composable
private fun StatBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,               // без обводки
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/* ---------- UI helpers (без изменений) ---------- */

@Composable
private fun RoomTypeAvatar(type: RoomType, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.surfaceVariant

    // небольшой акцент — только цвет иконки
    val iconTint = when (type) {
        RoomType.STANDARD -> MaterialTheme.colorScheme.primary
        RoomType.BATHROOM -> MaterialTheme.colorScheme.tertiary
        RoomType.KITCHEN -> MaterialTheme.colorScheme.secondary
        RoomType.OUTDOOR -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = when (type) {
        RoomType.STANDARD -> Icons.Rounded.Home
        RoomType.BATHROOM -> Icons.Rounded.Bathtub
        RoomType.KITCHEN -> Icons.Rounded.Kitchen
        RoomType.OUTDOOR -> Icons.Rounded.Park
    }

    Surface(
        color = bg,
        contentColor = iconTint,
        modifier = modifier,
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null)
        }
    }
}

private fun Double.format(d: Int) = "%.${d}f".format(this).replace(',', '.')
private fun roomTypeLabel(type: RoomType): String = when (type) {
    RoomType.STANDARD -> "Стандартная"
    RoomType.BATHROOM -> "Ванная"
    RoomType.KITCHEN -> "Кухня"
    RoomType.OUTDOOR -> "Улица"
}