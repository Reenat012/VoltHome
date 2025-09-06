package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel

/**
 * Экран «Распределение по фазам».
 * - 1 фаза: индикатор загрузки вводного, скрыты B/C, «Топ потребителей», «Куда уходит ток», таблица фазы A.
 * - 3 фазы: классический донат A/B/C + таблица по трём фазам.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhaseLoadScreen(
    viewModel: PhaseLoadViewModel = hiltViewModel(),
    explicationViewModel: ExplicationViewModel = hiltViewModel(),
    onGroupAction: (PhaseGroupItem) -> Unit = {},   // навигация к редактированию/разделению группы
) {
    // При входе пересчитываем группы/вводной, чтобы UI был консистентен
    LaunchedEffect(Unit) {
        explicationViewModel.recalcAndSaveGroups()
    }

    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Распределение по фазам") }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error.message ?: "Ошибка загрузки",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                PhaseLoadContent(
                    phaseLoads = uiState.data,
                    mode = uiState.mode,
                    incomerRating = uiState.incomer?.mcbRating,
                    thresholds = uiState.thresholds,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(Modifier) // паддинги со Scaffold не обязательны — контент сам задаёт отступы
                        .padding(padding)
                )
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