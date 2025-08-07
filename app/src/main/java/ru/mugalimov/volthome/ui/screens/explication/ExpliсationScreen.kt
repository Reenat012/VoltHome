package ru.mugalimov.volthome.ui.screens.explication

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

import ru.mugalimov.volthome.ui.screens.explication.Incomer.IncomerCard
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.GroupScreenState

//экран экспликации
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpliсationScreen(viewModel: ExplicationViewModel = hiltViewModel()) {
    // Собираем состояние из ViewModel
    val state by viewModel.uiState.collectAsState()
    Log.d("UI", "Текущее состояние UI: $state")

    LaunchedEffect(Unit) {
        viewModel.calculateGroups()
    }

    // Определяем фон в зависимости от темы
    val background = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.background(background)) {
        // Отображаем соответствующее состояние
        when (val s = state) {
            is GroupScreenState.Loading -> LoadingState()
            is GroupScreenState.Success -> {
                Column(Modifier.padding(16.dp)) {
                    // временно hasGroupRcds можно зашить как true, или вывести из VM позже
                    IncomerCard(
                        incomer = s.incomer,
                        groups = s.groups,
                        hasGroupRcds = true
                    )
                    Spacer(Modifier.height(16.dp))
                    GroupList(groups = s.groups, onRecalculate = { viewModel.calculateGroups() })
                }
            }
            is GroupScreenState.Error -> ErrorState(
                message = s.message,
                onRetry = { viewModel.calculateGroups() }
            )
        }
    }
}