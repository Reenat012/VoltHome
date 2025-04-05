package ru.mugalimov.volthome.ui.screens.rooms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.ui.viewmodel.RoomViewModel

/**
 * Экран добавления новой комнаты.
 * @param onBack Обработчик возврата на предыдущий экран
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRoomScreen(onBack: () -> Unit, viewModel: RoomViewModel = hiltViewModel()) {


    val defaultRooms by viewModel.defaultRooms.collectAsStateWithLifecycle()

    // Состояния для выпадающего списка
    var expanded by remember { mutableStateOf(false) }
    var selectedRooms by remember { mutableStateOf<DefaultRoom?>(null) }

    // Состояние для хранения названия комнаты
    var roomName by remember { mutableStateOf("") }

    // Scaffold — это базовый макет для экрана, который включает TopAppBar и контент.
    Scaffold(
        // TopAppBar — это верхняя панель с заголовком и кнопкой "Назад".
        topBar = {
            TopAppBar(
                title = { Text("Добавить комнату") },
                // Кнопка "Назад" с иконкой стрелки.
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
                },
                //кнопка для добавления комнаты
                actions = {
                    IconButton(
                        onClick = {
                            if (roomName.isNotBlank()) {
                                viewModel.addRoom(roomName)
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
                    value = selectedRooms?.name ?: "Выберите комнату",
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
                    defaultRooms.forEach { room ->
                        DropdownMenuItem(
                            text = { Text(room.name) },
                            onClick = {
                                selectedRooms = room
                                expanded = false
                                roomName = room.name
                            }
                        )
                    }
                }
            }

            // Поле ввода для названия комнаты.
            OutlinedTextField(
                value = roomName, // Текущее значение поля.
                onValueChange = { roomName = it }, // Обработчик изменения текста.
                label = { Text("Название") }, // Подпись поля
                modifier = Modifier.fillMaxWidth() // Занимает всю доступную ширину.
            )
        }

        // Spacer — это пустое пространство между элементами.
        Spacer(modifier = Modifier.height(16.dp))


    }
}