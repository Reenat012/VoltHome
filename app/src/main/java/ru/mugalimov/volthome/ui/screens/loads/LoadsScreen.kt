package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.entity.LoadEntity
import ru.mugalimov.volthome.model.Load
import ru.mugalimov.volthome.ui.components.ErrorView
import ru.mugalimov.volthome.ui.components.LoadingView
import ru.mugalimov.volthome.ui.screens.rooms.RoomList
import ru.mugalimov.volthome.ui.viewmodel.LoadsScreenViewModel

//экран нагрузок
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadsScreen(viewModel: LoadsScreenViewModel = hiltViewModel(),
                modifier: Modifier = Modifier
) {
    //подключаем наблюдателя за состоянием экрана
    val roomsWithLoads by viewModel.roomsWithLoads.collectAsState(initial = emptyList())


    LoadList(
        roomsWithLoads = roomsWithLoads,
        modifier = modifier
    )
}

fun toLoad(entity: LoadEntity): Load {
    return Load(
        id = entity.id,
        name = entity.name,
        current = entity.current,
        sumPower = entity.sumPower,
        countDevices = entity.countDevices,
        createdAt = entity.createdAt,
        roomId = entity.roomId
    )
}