package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.material3.ExperimentalMaterial3Api
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
    LaunchedEffect(roomId) {
        viewModel.calcLoad()
    }



    //подключаем наблюдателя за состоянием экрана
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        LoadingView()
    } else if (uiState.error != null) {
        ErrorView(error = uiState.error!!)
    } else LoadList(
        roomsWithLoads = uiState.loadsWithRoom,
        modifier = Modifier
    )

}

fun toLoad(entity: LoadEntity): Load {
    return Load(
        id = entity.id,
        name = entity.name,
        current = entity.currentRoom,
        sumPower = entity.powerRoom,
        countDevices = entity.countDevices,
        createdAt = entity.createdAt,
        roomId = entity.roomId
    )
}