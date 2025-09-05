package ru.mugalimov.volthome.ui.screens.rooms

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.ui.components.ErrorView
import ru.mugalimov.volthome.ui.components.LoadingView
import ru.mugalimov.volthome.ui.sheets.AddRoomSheet
import ru.mugalimov.volthome.ui.viewmodel.RoomViewModel
import ru.mugalimov.volthome.ui.viewmodel.RoomsAction
import ru.mugalimov.volthome.ui.viewmodel.RoomsViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    val deviceCounts by viewModel.deviceCounts.collectAsState()
    val phaseMode by viewModel.phaseMode.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddRoom by remember { mutableStateOf(false) }

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
                is RoomsAction.UserMessage -> snackbarHostState.showSnackbar(action.message)
                is RoomsAction.Error -> snackbarHostState.showSnackbar(
                    "Ошибка: ${action.throwable.localizedMessage ?: "неизвестная"}"
                )
            }
        }
    }

    val fabDisabled = isBusy
    val fabAlpha = if (fabDisabled) 0.5f else 1f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Комнаты") },
                actions = {
                    PhaseModeMenu(
                        mode = phaseMode,
                        onSelect = viewModel::setPhaseMode,
                        modifier = Modifier
                            .padding(end = 8.dp)     // небольшой отступ справа
                            .widthIn(min = 170.dp, max = 170.dp) // ограничиваем ширину
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!fabDisabled) showAddRoom = true },
                modifier = Modifier.alpha(fabAlpha)
            ) { Icon(Icons.Filled.Add, contentDescription = "Добавить") }
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingView()
            uiState.error != null -> ErrorView(uiState.error!!)
            else -> RoomList(
                rooms = uiState.rooms,
                deviceCounts = deviceCounts,          // ← ВАЖНО
                onDelete = viewModel::deleteRoom,
                modifier = Modifier.padding(padding),
                onClickRoom = onClickRoom
            )
        }
    }

    if (showAddRoom) {
        AddRoomSheet(
            defaultDevices = defaultDevices,
            roomTypes = RoomType.values().toList(),
            onConfirm = { name, type, selected ->
                addViewModel.createRoomWithDevices(name, type, selected)
                showAddRoom = false
            },
            onDismiss = { showAddRoom = false }
        )
    }
}