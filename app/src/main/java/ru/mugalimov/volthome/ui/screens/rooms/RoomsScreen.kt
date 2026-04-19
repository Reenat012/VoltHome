package ru.mugalimov.volthome.ui.screens.rooms

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.ui.components.ErrorView
import ru.mugalimov.volthome.ui.components.LoadingView
import ru.mugalimov.volthome.ui.viewmodel.RoomViewModel
import ru.mugalimov.volthome.ui.viewmodel.RoomsAction
import ru.mugalimov.volthome.ui.viewmodel.RoomsViewModel
import androidx.compose.ui.platform.testTag
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor

@SuppressLint("NotConstructor")
@Composable
fun RoomsScreen(
    onClickRoom: (Long) -> Unit,
    onAddRoom: () -> Unit, // legacy
    viewModel: RoomViewModel = hiltViewModel(),
    addViewModel: RoomsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val defaultDevices by addViewModel.defaultDevices.collectAsState()
    val isBusy by addViewModel.isBusy.collectAsState()
    val phaseMode by viewModel.phaseMode.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val showAddRoom: MutableState<Boolean> = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        addViewModel.actions.collect { action: RoomsAction ->
            when (action) {
                is RoomsAction.RoomCreated -> {
                    onClickRoom(action.roomId)

                    val res = snackbarHostState.showSnackbar(
                        message = "Комната создана (+${action.deviceIds.size})",
                        actionLabel = "Отменить",
                        withDismissAction = true
                    )

                    if (res == SnackbarResult.ActionPerformed) {
                        addViewModel.undoCreateRoom(action.roomId)
                    }
                }

                is RoomsAction.DevicesAdded -> Unit

                is RoomsAction.UserMessage -> {
                    snackbarHostState.showSnackbar(action.message)
                }

                is RoomsAction.Error -> {
                    snackbarHostState.showSnackbar(
                        "Ошибка: ${action.throwable.localizedMessage ?: "неизвестная"}"
                    )
                }
            }
        }
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

    val fabDisabled = isBusy
    val fabAlpha = if (fabDisabled) 0.5f else 1f

    Scaffold(
        // Внутренний Scaffold не должен повторно добавлять системные insets,
        // потому что верх уже обработан внешним контейнером.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!fabDisabled) {
                        showAddRoom.value = true
                    }
                },
                modifier = Modifier
                    .alpha(fabAlpha)
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
                    onSelect = viewModel::setPhaseMode,
                    modifier = Modifier.widthIn(min = 170.dp, max = 170.dp)
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
                            modifier = Modifier.fillMaxSize(),
                            onClickRoom = onClickRoom
                        )
                    }
                }
            }
        }
    }

    if (showAddRoom.value) {
        AddRoomSheet(
            defaultDevices = defaultDevices,
            roomTypes = RoomType.entries,
            onConfirm = { name, type, customizedRequests ->
                addViewModel.createRoomWithDevicesCustomized(
                    name = name,
                    roomType = type,
                    devices = customizedRequests
                )
                showAddRoom.value = false
            },
            onDismiss = {
                showAddRoom.value = false
            }
        )
    }
}