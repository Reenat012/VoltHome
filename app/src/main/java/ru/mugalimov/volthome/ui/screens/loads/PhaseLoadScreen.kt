package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.datastore.AppPreferences
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadMode
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallEntryPoint
import ru.mugalimov.volthome.ui.screens.loads.single.PhaseLoadSingleReportContent
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.PhaseLoadViewModel

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppPreferencesEntryPoint {
    fun appPreferences(): AppPreferences
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhaseLoadScreen(
    viewModel: PhaseLoadViewModel = hiltViewModel(),
    explicationViewModel: ExplicationViewModel = hiltViewModel(),
    onGroupAction: (PhaseGroupItem) -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val paywallBus = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PaywallEntryPoint::class.java
        ).paywallBus()
    }

    val appPreferences = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppPreferencesEntryPoint::class.java
        ).appPreferences()
    }

    val manualModeHintShown =
        appPreferences.manualModeHintShown.collectAsStateWithLifecycle(initialValue = false).value
    val firstDragHintShown =
        appPreferences.firstDragHintShown.collectAsStateWithLifecycle(initialValue = false).value

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
                title = {
                    val title = when (uiState.mode) {
                        PhaseMode.SINGLE -> "Состояние вводного аппарата"
                        PhaseMode.THREE -> "Распределение по фазам"
                    }
                    Text(title)
                }
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
                when (uiState.mode) {
                    PhaseMode.SINGLE -> {
                        PhaseLoadSingleReportContent(
                            uiState = uiState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                        )
                    }

                    PhaseMode.THREE -> {
                        PhaseLoadContent(
                            phaseLoads = uiState.data,
                            decisions = uiState.decisions,
                            phaseLoadMode = uiState.phaseLoadMode,
                            incomerRating = uiState.incomer?.mcbRating,
                            thresholds = uiState.thresholds,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            canDrag = canDrag,
                            onPaywall = { viewModel.onDnDLockedTapped() },
                            onGroupDropped = { groupId, phase -> viewModel.onGroupDragged(groupId, phase) },
                            onReset = { viewModel.onResetOverrides() },
                            onDecisionDetailsClick = { _ -> Unit },

                            manualModeHintShown = manualModeHintShown,
                            firstDragHintShown = firstDragHintShown,
                            markManualModeHintShown = { coroutineScope.launch { appPreferences.setManualModeHintShown() } },
                            markFirstDragHintShown = { coroutineScope.launch { appPreferences.setFirstDragHintShown() } },
                            onDropMissed = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Не попали в фазу. Перетащите группу на карточку A/B/C сверху."
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}