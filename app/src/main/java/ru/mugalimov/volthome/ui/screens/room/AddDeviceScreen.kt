package ru.mugalimov.volthome.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    roomId: Long,
    onBack: () -> Unit,
    viewModel: RoomDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadDefaultDevices() }
    val defaultDevices by viewModel.defaultDevices.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<DefaultDevice?>(null) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Добавить устройство",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            selectedDevice?.let { device ->
                                viewModel.addDevice(
                                    name = device.name,
                                    power = device.power,
                                    voltage = device.voltage,
                                    demandRatio = device.demandRatio,
                                    roomId = roomId,
                                    deviceType = device.deviceType,
                                    powerFactor = device.powerFactor,
                                    hasMotor = device.hasMotor,
                                    requiresDedicatedCircuit = device.requiresDedicatedCircuit
                                )
                            }
                            onBack()
                        },
                        enabled = selectedDevice != null,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Сохранить",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Шаблоны устройств
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Шаблоны устройств",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedDevice?.name ?: "Выберите шаблон устройства",
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_devices),
                                contentDescription = "Шаблоны устройств"
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        defaultDevices.forEach { device ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        device.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                onClick = {
                                    selectedDevice = device
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Отображение параметров выбранного устройства
            selectedDevice?.let { device ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Название устройства
                    DeviceInfoField(
                        label = "Название",
                        value = device.name,
                        icon = Icons.Filled.Edit
                    )

                    // Мощность
                    DeviceInfoField(
                        label = "Мощность",
                        value = "${device.power} Вт",
                        icon = Icons.Filled.Power
                    )

                    // Напряжение
                    DeviceInfoField(
                        label = "Напряжение",
                        value = "${device.voltage.value} В",
                        icon = Icons.Filled.Bolt
                    )

                    // Коэффициент спроса
                    DeviceInfoField(
                        label = "Коэффициент спроса",
                        value = device.demandRatio.toString(),
                        icon = Icons.Filled.Calculate
                    )

                    // Коэффициент мощности
                    DeviceInfoField(
                        label = "Коэффициент мощности",
                        value = device.powerFactor.toString(),
                        icon = Icons.Filled.Emergency
                    )
                }
            } ?: run {
                Text(
                    "Выберите устройство из списка",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoField(
    label: String,
    value: String,
    icon: ImageVector
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null
            )
        },
        colors = textFieldColors(),
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
        enabled = false
    )
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)
