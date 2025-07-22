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

/**
 * Экран добавления нового устройства.
 * @param onBack Обработчик возврата на предыдущий экран
 */
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
    var deviceName by remember { mutableStateOf("") }
    var devicePower by remember { mutableStateOf("") }
    var deviceVoltage by remember { mutableStateOf<Voltage?>(null) }
    var deviceDemandRatio by remember { mutableStateOf("") }
    var devicePowerFactor by remember { mutableStateOf("") }
    var deviceType by remember { mutableStateOf<DeviceType>(DeviceType.SOCKET)}
    var hasMotor by remember { mutableStateOf(false) }
    var requiresDedicatedCircuit by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val isTemplateSelected = selectedDevice != null // Флаг выбора шаблона

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
                            if (validateInput(deviceName, devicePower, deviceDemandRatio, devicePowerFactor)) {
                                viewModel.addDevice(
                                    name = deviceName,
                                    power = devicePower.toInt(),
                                    voltage = deviceVoltage!!,
                                    demandRatio = deviceDemandRatio.toDouble(),
                                    roomId = roomId,
                                    deviceType = deviceType,
                                    powerFactor = devicePowerFactor.toDouble(),
                                    hasMotor = hasMotor,
                                    requiresDedicatedCircuit = requiresDedicatedCircuit
                                )
                                onBack()
                            }
                        },
                        enabled = validateInput(deviceName, devicePower, deviceDemandRatio, devicePowerFactor),
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
                        value = selectedDevice?.name ?: "Выберите шаблон",
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
                                    deviceName = device.name
                                    devicePower = device.power.toString()
                                    deviceVoltage = device.voltage
                                    deviceDemandRatio = device.demandRatio.toString()
                                    deviceType = device.deviceType
                                    focusManager.clearFocus()
                                    devicePowerFactor = device.powerFactor.toString()
                                    hasMotor = device.hasMotor
                                    requiresDedicatedCircuit = device.requiresDedicatedCircuit

                                }
                            )
                        }
                    }
                }
            }

            // Основные параметры
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Название") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Иконка редактирования"
                        )
                    },
                    placeholder = { Text("Например: Стиральная машина") },
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = devicePower,
                    onValueChange = { if (it.isNumber()) devicePower = it },
                    label = { Text("Мощность (Вт)") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Power,
                            contentDescription = "Иконка мощности"
                        )
                    },
                    colors = textFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = {
                        Text(
                            "Вт",
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        )
                    },
//                    readOnly = isTemplateSelected, // Блокировка редактирования
//                    enabled = !isTemplateSelected // Визуальная индикация
                )

                VoltageSelector(
                    selectedVoltage = deviceVoltage,
                    onVoltageSelected = { deviceVoltage = it }
                )

                OutlinedTextField(
                    value = deviceDemandRatio,
                    onValueChange = { if (it.isDecimal()) deviceDemandRatio = it },
                    label = { Text("Коэффициент спроса") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Calculate,
                            contentDescription = "Иконка калькулятора"
                        )
                    },
                    colors = textFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = {
                        Text(
                            "0.0-1.0",
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        )
                    },
                    readOnly = isTemplateSelected, // Блокировка редактирования
                    enabled = !isTemplateSelected // Визуальная индикация
                )

                OutlinedTextField(
                    value = devicePowerFactor,
                    onValueChange = { if (it.isDecimal()) devicePowerFactor = it },
                    label = { Text("Коэффициент мощности") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Emergency,
                            contentDescription = "Иконка звездочка"
                        )
                    },
                    colors = textFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = {
                        Text(
                            "0.0-1.0",
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        )
                    },
                    readOnly = isTemplateSelected, // Блокировка редактирования
                    enabled = !isTemplateSelected // Визуальная индикация
                )
            }
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoltageSelector(
    selectedVoltage: Voltage?,
    onVoltageSelected: (Voltage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedVoltage?.value?.toString() ?: "Выберите напряжение",
            onValueChange = {},
            readOnly = true,
            enabled = false, // Блокируем взаимодействие
            label = { Text("Напряжение (В)") },
            leadingIcon = {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = "Иконка напряжения"
                )
            },
            // Убираем trailing icon (стрелочку)
            trailingIcon = null,
//            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = false,
            onDismissRequest = {
//                expanded = false
            }
        ) {
//            Voltage.entries.forEach { voltage ->
//                DropdownMenuItem(
//                    text = { Text("${voltage.value} В") },
//                    onClick = {
//                        onVoltageSelected(voltage)
//                        expanded = false
//                    }
//                )
//            }
        }
    }
}

private fun validateInput(name: String, power: String, ratio: String, powerFactory: String): Boolean {
    return name.isNotBlank() &&
            power.isNotBlank() &&
            ratio.isNotBlank() &&
            power.toIntOrNull() != null &&
            ratio.toDoubleOrNull()?.let { it in 0.0..1.0 } ?: false &&
            powerFactory.toDoubleOrNull()?.let { it in 0.0 .. 1.0} ?: false

}

private fun String.isNumber() = matches(Regex("^\\d+\$"))
private fun String.isDecimal() = matches(Regex("^\\d*(\\.\\d*)?\$"))
