package ru.mugalimov.volthome.ui.screens.room

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailAction
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel
import ru.mugalimov.volthome.ui.sheets.AllDevicesSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    vm: RoomDetailViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // состояние из VM
    val ui by vm.uiState.collectAsState()
    val room by vm.room.collectAsState()
    val defaultDevices by vm.defaultDevices.collectAsState()

    // локальное состояние: показать лист устройств
    var showAllDevices by remember { mutableStateOf(false) }

    // обработка одноразовых событий VM
    LaunchedEffect(Unit) {
        vm.actions.collect { action ->
            when (action) {
                is RoomDetailAction.DevicesAdded -> {
                    val res = snackbarHostState.showSnackbar(
                        message = "Добавлено ${action.deviceIds.size} устройств",
                        actionLabel = "Отменить",
                        withDismissAction = true
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        vm.undoAddDevices(action.deviceIds)
                    }
                }
                is RoomDetailAction.UserMessage -> snackbarHostState.showSnackbar(action.message)
                is RoomDetailAction.Error -> snackbarHostState.showSnackbar(
                    "Ошибка: ${action.throwable.localizedMessage ?: "неизвестная"}"
                )
            }
        }
    }

    val fabDisabled = ui.isLoading
    val fabAlpha = if (fabDisabled) 0.5f else 1f

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = room?.name ?: "Комната",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!fabDisabled) showAllDevices = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Добавить устройства") },
                modifier = Modifier.alpha(fabAlpha)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.isLoading && ui.devices.isEmpty() -> {
                    // первичная загрузка
                    LoadingState()
                }
                ui.error != null && ui.devices.isEmpty() -> {
                    ErrorState(message = ui.error?.localizedMessage ?: "Ошибка загрузки")
                }
                else -> {
                    if (ui.devices.isEmpty()) {
                        EmptyState(onAdd = { showAllDevices = true })
                    } else {
                        DevicesList(devices = ui.devices)
                    }
                }
            }
        }
    }

    if (showAllDevices) {
        AllDevicesSheet(
            defaultDevices = defaultDevices,
            onConfirm = { selected: List<DefaultDevice> ->
                val withQty: List<Pair<DefaultDevice, Int>> = selected.map { it to 1 }
                vm.addDevicesToCurrentRoom(withQty)
                showAllDevices = false
            },
            onDismiss = { showAllDevices = false }
        )
    }
}

/* ===================== UI-элементы экрана ===================== */

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DevicesList(
    devices: List<Device>,
    // onClick: (Device) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(devices, key = { it.id ?: it.hashCode().toLong() }) { d ->
            DeviceRow(d)
        }
    }
}

@Composable
private fun DeviceRow(d: Device) {
    // Подстрой поля под твою модель Device, ниже — частые поля:
    // name, power (Вт), powerFactor, demandRatio, voltage (value/type)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistChip(label = { Text("${d.power} Вт") }, onClick = {}, enabled = false)
                AssistChip(label = { Text("PF ${trim2(d.powerFactor)}") }, onClick = {}, enabled = false)
                AssistChip(label = { Text("DR ${trim2(d.demandRatio)}") }, onClick = {}, enabled = false)
                val v = d.voltage
                AssistChip(
                    label = { Text("${v.value}V • ${v.type.name}") },
                    onClick = {},
                    enabled = false
                )
            }
        }
    }
}

/* ===================== helpers ===================== */

private fun trim2(x: Double): String = "%,.2f".format(x)

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("В этой комнате пока нет устройств")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAdd) { Text("Добавить устройства") }
    }
}