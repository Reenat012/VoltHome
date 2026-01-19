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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallEntryPoint
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.PhaseLoadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhaseLoadScreen(
    viewModel: PhaseLoadViewModel = hiltViewModel(),
    explicationViewModel: ExplicationViewModel = hiltViewModel(),
    onGroupAction: (PhaseGroupItem) -> Unit = {},
) {
    LaunchedEffect(Unit) {
        explicationViewModel.recalcAndSaveGroups()
    }

    val context = LocalContext.current
    val paywallBus = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PaywallEntryPoint::class.java
        ).paywallBus()
    }

    val canDrag = LocalUserPlan.current.capabilities.phaseDragAndDrop
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

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
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                    decisions = uiState.decisions,
                    mode = uiState.mode,
                    phaseLoadMode = uiState.phaseLoadMode,
                    incomerRating = uiState.incomer?.mcbRating,
                    thresholds = uiState.thresholds,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    canDrag = canDrag,
                    onEnterManualMode = { viewModel.onEnterManualMode() },
                    onPaywall = { viewModel.onDnDLockedTapped() },
                    onGroupDropped = { groupId, phase -> viewModel.onGroupDragged(groupId, phase) },
                    onReset = { viewModel.onResetOverrides() },
                    onDecisionDetailsClick = { _ -> Unit }
                )
            }
        }
    }
}