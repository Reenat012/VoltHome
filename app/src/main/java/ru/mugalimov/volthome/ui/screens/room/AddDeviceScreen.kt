package ru.mugalimov.volthome.ui.screens.room

import android.content.ContentValues.TAG
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel
import java.util.Date

/**
 * Экран добавления нового устройства.
 * @param onBack Обработчик возврата на предыдущий экран
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    roomId: Long, // Получаем roomId из аргументов навигации
    onBack: () -> Unit,
    viewModel: RoomDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadDefaultDevices()
    }



    val defaultDevices by viewModel.defaultDevices.collectAsStateWithLifecycle()

    // Состояния для выпадающего списка
    var expanded by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<DefaultDevice?>(null) }

    // Состояние для хранения названия комнаты
    var deviceName by remember { mutableStateOf("") }
    var devicePower by remember { mutableStateOf("") }
    var deviceVoltage by remember { mutableStateOf<Voltage?>(null) }
    var deviceDemandRatio by remember { mutableStateOf("") }
    var deviceType by remember { mutableStateOf<DeviceType>(DeviceType.SOCKET)}


    // Scaffold — это базовый макет для экрана, который включает TopAppBar и контент.
    Scaffold(
        // TopAppBar — это верхняя панель с заголовком и кнопкой "Назад".
        topBar = {
            TopAppBar(
                title = { Text("Добавить устройство") },
                // Кнопка "Назад" с иконкой стрелки.
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
                },
                //кнопка для добавления комнаты
                actions = {
                    IconButton(
                        onClick = {
                            if (deviceName.isNotBlank()
                                && devicePower.isNotBlank()
                                && deviceDemandRatio.isNotBlank()
                            ) {
                                viewModel.addDevice(
                                    name = deviceName,
                                    power = devicePower.toInt(),
                                    voltage = deviceVoltage!!,
                                    demandRatio = deviceDemandRatio.toDouble(),
                                    roomId = roomId, // Используем переданный roomId, а не id из viewmodel
                                    deviceType = deviceType
                                )
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, "Сохранить") // Иконка "галочка"}
                    }
                }
            )

        }
    ) { padding ->


        // Column — это вертикальный контейнер для размещения элементов.
        Column(
            modifier = Modifier
                .padding(padding) // Отступы от Scaffold.
                .fillMaxSize() // Занимает весь доступный размер.
                .padding(16.dp) // Внутренние отступы.

        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedDevice?.name ?: "Выберите устройство",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    defaultDevices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name) },
                            onClick = {
                                selectedDevice = device
                                expanded = false
                                deviceName = device.name
                                devicePower = device.power.toString()
                                deviceVoltage = device.voltage
                                deviceDemandRatio = device.demandRatio.toString()
                                deviceType = device.deviceType
                            }
                        )
                    }
                }
            }


            // Поле ввода для названия комнаты.
            OutlinedTextField(
                value = deviceName, // Текущее значение поля.
                onValueChange = { deviceName = it }, // Обработчик изменения текста.
                label = { Text("Название") }, // Подпись поля
                modifier = Modifier.fillMaxWidth() // Занимает всю доступную ширину.
            )
            // Поле для ввода мощности устройства
            OutlinedTextField(
                value = devicePower, // Текущее значение поля.
                onValueChange = { devicePower = it }, // Обработчик изменения текста.
                label = { Text("Мощность, Вт") }, // Подпись поля
                modifier = Modifier.fillMaxWidth() // Занимает всю доступную ширину.
            )

            // Поле ввода класса напряжения устройства
            OutlinedTextField(
                value = deviceVoltage?.value.toString(), // Текущее значение поля.
                onValueChange = {}, // Обработчик изменения текста.
                label = { Text("Класс напряжения, В") }, // Подпись поля
                modifier = Modifier.fillMaxWidth(), // Занимает всю доступную ширину.
                readOnly = true, // Запрещаем редактирование
                enabled = true // Поле визуально активно (можно фокусироваться)
            )
            // Поле ввода к-та спроса
            OutlinedTextField(
                value = deviceDemandRatio, // Текущее значение поля.
                onValueChange = { deviceDemandRatio = it }, // Обработчик изменения текста.
                label = { Text("Коэффициент спроса") }, // Подпись поля
                modifier = Modifier.fillMaxWidth() // Занимает всю доступную ширину.
            )
        }

        // Spacer — это пустое пространство между элементами.
        Spacer(modifier = Modifier.height(16.dp))


    }
}
