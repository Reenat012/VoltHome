package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhaseLoadScreen(
    viewModel: PhaseLoadViewModel = hiltViewModel()
) {
    val explicationVm: ExplicationViewModel = hiltViewModel()
    LaunchedEffect(Unit) { explicationVm.recalcAndSaveGroups() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Распределение по фазам") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> LoadingView()
                uiState.error != null -> ErrorView(
                    error = uiState.error!!,
                    onRetry = { /* перезапуск потока не нужен — Flow живой;
                                 если хочешь ручной триггер, сделай метод в VM */ }
                )
                else -> {
                    val items: List<PhaseLoadItem> = uiState.data
                    if (items.isEmpty()) {
                        EmptyView(
                            text = "Пока нет данных для отображения.\nДобавьте устройства на первом экране."
                        )
                    } else {
                        PhaseLoadContent(
                            phaseLoads = items,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/* ====== helper UI ====== */

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(
    error: Throwable,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Ошибка загрузки",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = (error.message ?: error.toString()),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

@Composable
private fun EmptyView(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}