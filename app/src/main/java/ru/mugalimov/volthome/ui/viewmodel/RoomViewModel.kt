package ru.mugalimov.volthome.ui.viewmodel

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.VoltageType
import java.util.Date

@HiltViewModel
class RoomViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val deviceRepository: DeviceRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    /** Одноразовые события в UI: снекбары и т.п. */
    private val _actions = MutableSharedFlow<RoomsAction>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val actions: SharedFlow<RoomsAction> = _actions

    private val _uiState = MutableStateFlow(RoomUiState())
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    private val _defaultRooms = MutableStateFlow<List<DefaultRoom>>(emptyList())
    val defaultRooms: StateFlow<List<DefaultRoom>> = _defaultRooms.asStateFlow()

    private val _deviceCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val deviceCounts: StateFlow<Map<Long, Int>> = _deviceCounts.asStateFlow()

    // Текущее значение режима для UI (с дефолтом THREE)
    val phaseMode: StateFlow<PhaseMode> =
        preferencesRepository.phaseMode
            .stateIn(viewModelScope, SharingStarted.Lazily, PhaseMode.THREE)

    init {
        observeRooms()
        loadDefaultRooms()
    }

    fun setPhaseMode(mode: PhaseMode) {
        viewModelScope.launch {
            // Инвариант: нельзя включать 1φ, если в проекте есть 3φ устройства.
            if (mode == PhaseMode.SINGLE) {
                val rooms = uiState.value.rooms
                val has3Phase = rooms.any { room ->
                    val devices = deviceRepository.getAllDevicesByRoomId(room.id)
                    devices.any { it.voltage.type == VoltageType.AC_3PHASE }
                }

                if (has3Phase) {
                    emitUserMessage(
                        "В проекте есть 3-фазные устройства. Переключение в 1-фазный режим невозможно."
                    )
                    return@launch
                }
            }

            preferencesRepository.setPhaseMode(mode)
        }
    }

    private fun emitUserMessage(msg: String) {
        viewModelScope.launch { _actions.emit(RoomsAction.UserMessage(msg)) }
    }

    private fun observeRooms() {
        viewModelScope.launch {
            roomRepository.observeRooms()
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e
                        )
                    }
                }
                .collect { rooms ->
                    _uiState.update {
                        it.copy(
                            rooms = rooms,
                            isLoading = false,
                            error = null
                        )
                    }

                    val counts = rooms.associate { room ->
                        val cnt = deviceRepository.getAllDevicesByRoomId(room.id).size
                        room.id to cnt
                    }
                    _deviceCounts.value = counts
                }
        }
    }

    fun addRoom(name: String, roomType: RoomType) {
        viewModelScope.launch {
            executeOperation {
                try {
                    _uiState.update { it.copy(isLoading = true) }
                    validateName(name)
                    val newRoom = Room(name = name, createdAt = Date(), roomType = roomType)
                    Log.d("addRoom VM", "$roomType")
                    roomRepository.addRoom(newRoom)
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            error = e,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun deleteRoom(roomId: Long) {
        viewModelScope.launch {
            executeOperation {
                roomRepository.deleteRoom(roomId)
            }
        }
    }

    fun updateRoomWithDevices(roomId: Long) {
        viewModelScope.launch {
            try {
                val devices = deviceRepository.getAllDevicesByRoomId(roomId)
                val room = roomRepository.getRoomById(roomId)
                val updateRoom = room?.copy(devices = devices)

                if (room != null) {
                    roomRepository.updateRoom(room)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e) }
            }
        }
    }

    private fun loadDefaultRooms() {
        viewModelScope.launch {
            try {
                _defaultRooms.value = roomRepository.getDefaultRooms().first()
                Log.d(TAG, "defaultRooms.size=${defaultRooms.value.size}")
            } catch (e: Exception) {
                Log.e("LOAD_ERROR", "Error loading rooms", e)
            }
        }
    }

    private suspend fun <T> executeOperation(block: suspend () -> T) {
        startLoading()
        try {
            block()
            clearError()
        } catch (e: Exception) {
            handleError(e)
        } finally {
            stopLoading()
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank()) {
            throw IllegalArgumentException("Имя комнаты не может быть пустым")
        }
    }

    private fun updateRooms(rooms: List<Room>) {
        _uiState.update { it.copy(rooms = rooms, error = null) }
    }

    private fun handleError(e: Throwable) {
        _uiState.update { it.copy(error = e) }
    }

    private fun startLoading() {
        _uiState.update { it.copy(isLoading = true) }
    }

    private fun stopLoading() {
        _uiState.update { it.copy(isLoading = false) }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class RoomUiState(
    val rooms: List<Room> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)