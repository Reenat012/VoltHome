package ru.mugalimov.volthome.ui.screens.room

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.entity.DeviceEntity
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel
import java.util.Date

/**
 * Экран добавления нового устройства.
 * @param onBack Обработчик возврата на предыдущий экран
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    roomId: Int, // Получаем roomId из аргументов навигации
    onBack: () -> Unit,
    viewModel: RoomDetailViewModel = hiltViewModel()
) {

    // Состояние для хранения названия комнаты
    var deviceName by remember { mutableStateOf("") }
    var devicePower by remember { mutableStateOf("") }
    var deviceVoltage by remember { mutableStateOf("") }
    var deviceDemandRatio by remember { mutableStateOf("") }


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
                                && deviceVoltage.isNotBlank()
                                && deviceDemandRatio.isNotBlank()
                            ) {
                                viewModel.addDevice(
                                    name = deviceName,
                                    power = devicePower.toInt(),
                                    voltage = deviceVoltage.toInt(),
                                    demandRatio = deviceDemandRatio.toDouble(),
                                    roomId = roomId // Используем переданный roomId, а не id из viewmodel
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
                value = deviceVoltage, // Текущее значение поля.
                onValueChange = { deviceVoltage = it }, // Обработчик изменения текста.
                label = { Text("Класс напряжения, В") }, // Подпись поля
                modifier = Modifier.fillMaxWidth() // Занимает всю доступную ширину.
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