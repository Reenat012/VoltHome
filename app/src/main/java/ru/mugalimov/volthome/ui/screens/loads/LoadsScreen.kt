package ru.mugalimov.volthome.ui.screens.loads

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.domain.model.Load
import ru.mugalimov.volthome.ui.components.ErrorView
import ru.mugalimov.volthome.ui.components.LoadingView
import ru.mugalimov.volthome.ui.screens.rooms.RoomList
import ru.mugalimov.volthome.ui.viewmodel.LoadsScreenViewModel

//экран нагрузок
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadsScreen(
    viewModel: LoadsScreenViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    roomId: Long, // Получаем roomId из аргументов навигации
) {
    // Вызываем расчет суммы при создании экрана или изменении roomId
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    //подключаем наблюдателя за состоянием экрана
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Нагрузки") })
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingView()
            uiState.error != null -> ErrorView(uiState.error!!)
            else -> LoadList(
                items = uiState.items,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

