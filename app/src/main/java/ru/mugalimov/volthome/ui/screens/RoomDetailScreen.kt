package ru.mugalimov.volthome.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

import ru.mugalimov.volthome.viewmodel.RoomDetailViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

//Экран отдельной комнаты
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    roomId: Int,
    onBack: () -> Unit,
    viewModel: RoomDetailViewModel = hiltViewModel()
) {
    //подлкючаем наблюдателя за комнатами
    val room by viewModel.room.collectAsState()

    //рисуем интерфейс
    Scaffold(
        topBar = {
            //Создаем верхнее меню
            TopAppBar(
                title = { Text(room?.name ?: "Загрузка...") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        when {
            room == null -> Text("Комната не найдена")
            else -> Column(
                modifier = Modifier.padding(padding)
            ) {
                Text("ID: ${room!!.id}")
                Text("Создана: ${room!!.createdAt}")
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun LocalDateTime.format(): String {
    return DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm")
        .format(this)
}