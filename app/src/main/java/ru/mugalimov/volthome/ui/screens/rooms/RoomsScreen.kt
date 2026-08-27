package ru.mugalimov.volthome.ui.screens.rooms

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import ru.mugalimov.volthome.ui.components.ErrorView
import ru.mugalimov.volthome.ui.components.LoadingView
import ru.mugalimov.volthome.ui.onboarding.OnboardingRuntimeEntryPoint
import ru.mugalimov.volthome.ui.onboarding.hints.BaseHints
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.model.RoomsOnboardingFacts
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor
import ru.mugalimov.volthome.ui.viewmodel.RoomViewModel
import ru.mugalimov.volthome.ui.viewmodel.RoomsAction

@SuppressLint("NotConstructor")
@Composable
fun RoomsScreen(
    onClickRoom: (Long) -> Unit,
    onAddRoom: () -> Unit,
    onReconfigureProject: () -> Unit,
    viewModel: RoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val phaseMode by viewModel.phaseMode.collectAsStateWithLifecycle()
    val onboardingFacts by viewModel.onboardingFacts.collectAsStateWithLifecycle(
        initialValue = RoomsOnboardingFacts()
    )

    val context = LocalContext.current
    val onboardingCoordinator = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OnboardingRuntimeEntryPoint::class.java
        ).onboardingCoordinator()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    /**
     * Единственная orchestration point для Rooms onboarding.
     *
     * Запрещено вызывать tryShow() где-то ещё внутри этого экрана.
     */
    LaunchedEffect(onboardingFacts) {
        Log.d(
            "ONBOARD_ROOMS",
            buildString {
                append("ORCH_BEGIN")
                append(" roomsCount=").append(onboardingFacts.roomsCount)
                append(" isLoading=").append(onboardingFacts.isLoading)
            }
        )

        val hint = BaseHints.forRooms(onboardingFacts)
        if (hint == null) {
            Log.d(
                "ONBOARD_ROOMS",
                "ORCH_SKIP reason=NO_HINT roomsCount=${onboardingFacts.roomsCount} isLoading=${onboardingFacts.isLoading}"
            )
            return@LaunchedEffect
        }

        Log.d(
            "ONBOARD_ROOMS",
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
            "ONBOARD_ROOMS",
            "ORCH_END hintId=${hint.hintId.name} accepted=$accepted"
        )
    }

    LaunchedEffect(Unit) {
        viewModel.actions.collect { action: RoomsAction ->
            when (action) {
                is RoomsAction.UserMessage -> {
                    snackbarHostState.showSnackbar(action.message)
                }

                is RoomsAction.Error -> {
                    snackbarHostState.showSnackbar(
                        "Ошибка: ${action.throwable.localizedMessage ?: "неизвестная"}"
                    )
                }

                else -> Unit
            }
        }
    }

    Scaffold(
        // Внутренний Scaffold не должен повторно добавлять системные insets,
        // потому что верх уже обработан внешним контейнером.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRoom,
                modifier = Modifier
                    .testTag(OnboardingTargetTag.ROOMS_ADD_FAB.rawTag)
                    .onboardingAnchor(
                        targetTag = OnboardingTargetTag.ROOMS_ADD_FAB,
                        screenId = OnboardingScreen.ROOMS
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Добавить"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Заголовок и переключатель сети рисуем как обычный контент,
            // а не через TopAppBar, чтобы не было двойного верхнего отступа.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Комнаты",
                    style = MaterialTheme.typography.headlineSmall
                )

                PhaseModeMenu(
                    mode = phaseMode,
                    onOpenConfiguration = onReconfigureProject
                )
            }

            Box(
                modifier = Modifier.weight(1f)
            ) {
                when {
                    uiState.isLoading -> {
                        LoadingView()
                    }

                    uiState.error != null -> {
                        ErrorView(uiState.error!!)
                    }

                    else -> {
                        RoomList(
                            rooms = uiState.roomsPreview,
                            onDelete = viewModel::deleteRoom,
                            onAddRoom = onAddRoom,
                            modifier = Modifier.fillMaxSize(),
                            onClickRoom = onClickRoom
                        )
                    }
                }
            }
        }
    }

}
