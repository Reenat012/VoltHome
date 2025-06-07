package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import ru.mugalimov.volthome.ui.components.ErrorView
import ru.mugalimov.volthome.ui.components.LoadingView
import ru.mugalimov.volthome.ui.screens.loads.LoadList
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel

//экран экспликации
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpliсationScreen(
    viewModel: ExplicationViewModel = hiltViewModel(),
) {
    // Вызываем расчет суммы при создании экрана или изменении roomId
    LaunchedEffect(Unit) {
        viewModel.calcGroups()

        viewModel.getAllGroup()
    }

    TopAppBar(title = { Text("Нагрузки") })

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Экспликация") })
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingView()
            uiState.error != null -> ErrorView(uiState.error!!)
            else -> GroupList(
                groups = uiState.groups,
                modifier = Modifier.padding(padding)
            )
        }
    }
}