package ru.mugalimov.volthome.ui.screens.loads

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadMode
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.onboarding.OnboardingRuntimeEntryPoint
import ru.mugalimov.volthome.ui.onboarding.hints.BaseHints
import ru.mugalimov.volthome.ui.onboarding.model.LoadsOnboardingFacts
import ru.mugalimov.volthome.ui.paywall.PaywallEntryPoint
import ru.mugalimov.volthome.ui.screens.loads.single.PhaseLoadSingleReportContent
import ru.mugalimov.volthome.ui.viewmodel.PhaseLoadViewModel

@Composable
fun PhaseLoadScreen(
    viewModel: PhaseLoadViewModel = hiltViewModel(),
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

    val onboardingCoordinator = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OnboardingRuntimeEntryPoint::class.java
        ).onboardingCoordinator()
    }

    val canDrag = LocalUserPlan.current.capabilities.phaseDragAndDrop
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val groupsCount = viewModel.groupsCount.collectAsStateWithLifecycle().value
    val onboardingFacts = viewModel.onboardingFacts.collectAsStateWithLifecycle(
        initialValue = LoadsOnboardingFacts()
    ).value

    // Заголовок оставляем, но теперь рисуем его в контенте,
    // а не через внутренний TopAppBar, чтобы не было двойного верхнего отступа.
    val title = when (uiState.mode) {
        PhaseMode.SINGLE -> "Состояние вводного аппарата"
        PhaseMode.THREE -> "Распределение по фазам"
    }

    val snackbarHostState = remember { SnackbarHostState() }

    /**
     * Единственная orchestration point для Loads onboarding.
     */
    /**
     * Единственная orchestration point для Loads onboarding.
     */
    LaunchedEffect(onboardingFacts) {
        Log.d(
            "ONBOARD_LOADS",
            buildString {
                append("ORCH_BEGIN")
                append(" groupsCount=").append(onboardingFacts.groupsCount)
                append(" phaseMode=").append(onboardingFacts.phaseMode.name)
                append(" isLoading=").append(onboardingFacts.isLoading)
            }
        )

        val hint = BaseHints.forLoads(onboardingFacts)
        if (hint == null) {
            Log.d(
                "ONBOARD_LOADS",
                buildString {
                    append("ORCH_SKIP reason=NO_HINT")
                    append(" groupsCount=").append(onboardingFacts.groupsCount)
                    append(" phaseMode=").append(onboardingFacts.phaseMode.name)
                    append(" isLoading=").append(onboardingFacts.isLoading)
                }
            )
            return@LaunchedEffect
        }

        Log.d(
            "ONBOARD_LOADS",
            "ORCH_CANDIDATE hintId=${hint.hintId.name} priority=${hint.priority} targetTag=${hint.targetTag?.rawTag ?: "null"}"
        )

        val accepted = onboardingCoordinator.tryShow(
            hintId = hint.hintId,
            screen = hint.screen,
            targetTag = hint.targetTag,
            title = hint.title,
            body = hint.body
        )

        Log.d(
            "ONBOARD_LOADS",
            "ORCH_END hintId=${hint.hintId.name} accepted=$accepted"
        )
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    @Composable
    fun LoadsEmptyState(
        onRecalc: () -> Unit
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Группы пока не созданы")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRecalc) {
                    Text("Пересчитать")
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Заголовок экрана как обычный блок контента.
            // Так он занимает адекватное место и не конфликтует с внешним AppBar.
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            // Основную область даём через weight(1f),
            // чтобы она занимала оставшуюся высоту под заголовком.
            Box(
                modifier = Modifier.weight(1f)
            ) {
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
                        // Пустоту определяем только по количеству групп,
                        // потому что uiState.data может содержать "каркас фаз".
                        if (groupsCount == 0) {
                            val isManual = uiState.phaseLoadMode == PhaseLoadMode.MANUAL

                            LoadsEmptyState(
                                onRecalc = {
                                    if (isManual) {
                                        // В MANUAL пересчёт не делаем.
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                "В ручном режиме пересчёт недоступен. Сначала сбросьте изменения."
                                            )
                                        }
                                    } else {
                                        // В AUTO вызываем ближайшее доступное действие.
                                        viewModel.onResetOverrides()
                                    }
                                }
                            )
                        } else {
                            when (uiState.mode) {
                                PhaseMode.SINGLE -> {
                                    PhaseLoadSingleReportContent(
                                        uiState = uiState,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                PhaseMode.THREE -> {
                                    PhaseLoadContent(
                                        phaseLoads = uiState.data,
                                        decisions = uiState.decisions,
                                        phaseLoadMode = uiState.phaseLoadMode,
                                        incomerRating = uiState.incomer?.mcbRating,
                                        thresholds = uiState.thresholds,
                                        modifier = Modifier.fillMaxSize(),
                                        canDrag = canDrag,
                                        onPaywall = {
                                            // Пока оставляю вызов как был,
                                            // чтобы не менять существующее поведение.
                                            paywallBus
                                            viewModel.onDnDLockedTapped()
                                        },
                                        onGroupDropped = { groupId, phase ->
                                            viewModel.onGroupDragged(groupId, phase)
                                        },
                                        onReset = { viewModel.onResetOverrides() },
                                        onDecisionDetailsClick = { _ -> Unit },
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
        }
    }
}