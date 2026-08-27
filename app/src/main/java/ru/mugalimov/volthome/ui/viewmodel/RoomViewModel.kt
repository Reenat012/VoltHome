package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.data.repository.observeResolved
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.RoomWithDevicesPreview
import ru.mugalimov.volthome.domain.use_case.DeleteRoomUseCase
import ru.mugalimov.volthome.ui.onboarding.model.RoomsOnboardingFacts
import ru.mugalimov.volthome.ui.screens.rooms.RoomUiState
import ru.mugalimov.volthome.ui.screens.rooms.RoomWithDevicesPreviewUi

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val deleteRoomUseCase: DeleteRoomUseCase,
    private val preferencesRepository: PreferencesRepository,
    activeProjectDataStore: ActiveProjectDataStore,
    projectSetupRepository: ProjectSetupRepository
) : ViewModel() {

    private val _actions = MutableSharedFlow<RoomsAction>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val actions: SharedFlow<RoomsAction> = _actions

    val phaseMode: StateFlow<PhaseMode> =
        activeProjectDataStore.activeProjectId
            .flatMapLatest { projectId ->
                if (projectId.isNullOrBlank()) {
                    preferencesRepository.phaseMode
                } else {
                    val legacyFallbackMode = preferencesRepository.phaseMode.first()
                    projectSetupRepository.observeResolved(projectId, legacyFallbackMode)
                        .map { setup -> setup.phaseMode }
                }
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Lazily, PhaseMode.THREE)

    val uiState: StateFlow<RoomUiState> =
        roomRepository.observeRoomsWithDevicesPreview()
            .map { list -> list.map { it.toUi() } }
            .distinctUntilChanged() // ✅ одинаковые списки не будут лишний раз триггерить UI
            .map { roomsPreview -> RoomUiState(isLoading = false, roomsPreview = roomsPreview) }
            .onStart { emit(RoomUiState(isLoading = true)) }
            .catch { t -> emit(RoomUiState(isLoading = false, error = t)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                RoomUiState(isLoading = true)
            )

    /**
     * Канонический источник фактов для Rooms onboarding.
     *
     * Источник:
     * - только RoomViewModel.uiState
     */
    val onboardingFacts: StateFlow<RoomsOnboardingFacts> =
        uiState
            .map { state ->
                RoomsOnboardingFacts(
                    roomsCount = state.roomsPreview.size,
                    isLoading = state.isLoading
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                RoomsOnboardingFacts()
            )

    fun deleteRoom(roomId: Long) {
        viewModelScope.launch {
            try {
                deleteRoomUseCase(roomId)
            } catch (t: Throwable) {
                _actions.emit(RoomsAction.Error(t))
            }
        }
    }

    private fun RoomWithDevicesPreview.toUi(): RoomWithDevicesPreviewUi {
        return RoomWithDevicesPreviewUi(
            roomId = roomId,
            name = name,
            roomType = roomType,
            devicesCount = devicesCount,
            devicesPreview = devicesPreview.map { it.name }.toList() // ✅ immutable snapshot
        )
    }
}
