package ru.mugalimov.volthome.ui.screens.rooms

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import ru.mugalimov.volthome.viewmodel.RoomViewModel

/**
 * Экран отображения списка комнат.
 * @param onAddRoom Обработчик нажатия кнопки добавления комнаты
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("NotConstructor")
@Composable
fun RoomsScreen(
    onClickRoom: (Int) -> Unit,  // Обработчик клика на комнату
    onAddRoom: () -> Unit, // Обработчик клика на кнопку "Добавить комнату"
    viewModel: RoomViewModel = hiltViewModel()
) {

    //подключаем наблюдателя за состоянием экрана
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Комнаты") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRoom) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingView()
            uiState.error != null -> ErrorView(uiState.error!!)
            else -> RoomList(
                rooms = uiState.rooms,
                onDelete = viewModel::deleteRoom,
                modifier = Modifier.padding(padding),
                onClickRoom = onClickRoom
            )
        }
    }
}
