package ru.mugalimov.volthome.ui.screens.room

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.ui.components.ErrorView
import ru.mugalimov.volthome.ui.components.LoadingView
import ru.mugalimov.volthome.ui.screens.rooms.RoomList

import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel
import ru.mugalimov.volthome.ui.viewmodel.RoomViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

//Экран отдельной комнаты
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    roomId: Long, // Получаем roomId из аргументов навигации
    onClickDevice: (Int) -> Unit,  // Обработчик клика на устройство
    onAddDevice: () -> Unit, // Обработчик клика на кнопку "Добавить устройство"
    onBack: () -> Unit,
    viewModel: RoomDetailViewModel = hiltViewModel<RoomDetailViewModel>()
) {
    //подлкючаем наблюдателя за комнатами
    val room by viewModel.room.collectAsState()

    //подлкючаем наблюдателя за комнатами
    val uiState by viewModel.uiState.collectAsState()

    //рисуем интерфейс
    Scaffold(
        topBar = {
            //Создаем верхнее меню
            TopAppBar(
                title = { Text("${room?.name}" ?: "Загрузка... ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDevice) { //onAddDevice содержит логику навигации с roomId
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingView()
            uiState.error != null -> ErrorView(uiState.error!!)
            else -> DeviceList(
                devices = uiState.devices,
                onDelete = viewModel::deleteDevice,
                modifier = Modifier.padding(padding),
//                onClickDevice = onClickDevice
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun LocalDateTime.format(): String {
    return DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm")
        .format(this)
}