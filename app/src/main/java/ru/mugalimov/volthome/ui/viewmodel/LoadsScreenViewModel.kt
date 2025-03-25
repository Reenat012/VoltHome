package ru.mugalimov.volthome.ui.viewmodel

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.model.Load
import ru.mugalimov.volthome.model.Room
import ru.mugalimov.volthome.repository.DeviceRepository
import ru.mugalimov.volthome.repository.LoadsRepository
import ru.mugalimov.volthome.repository.RoomRepository
import javax.inject.Inject

class LoadsScreenViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val loadRepository: LoadsRepository,
    savedStateHandle: SavedStateHandle // Конверт с запросом (ID комнаты)
) : ViewModel() {
    private val _roomId = savedStateHandle.get<Int>("roomId") ?: 0
    val roomId = _roomId

    private val _rooms = MutableStateFlow<Room?>(null)
    val rooms: StateFlow<Room?> = _rooms.asStateFlow()

    private val _uiState = MutableStateFlow(LoadUiState())
    val uiState: StateFlow<LoadUiState> = _uiState.asStateFlow()

    init {
        if (_roomId == 0) {
            Log.e(TAG, "Ошибка: roomId не передан или равен 0")
        }


    }

    private fun loadRooms() {
        viewModelScope.launch {
            try {
                val foundRooms = roomRepository.getRoomById(_roomId)

                _rooms.value = foundRooms
            } catch (e: Exception) {
                //если комната не найдена, оставляем хранилище пустым
                _rooms.value = null
            }
        }
    }

    private fun observeLoads() {
        viewModelScope.launch {
            loadRepository.observeLoads()
                // Начинаем поиск нагрузок
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                // Если ошибка
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e
                        )
                    }
                }
                // Обновляем состояние при успехе
                .collect {
                    loads ->
                    _uiState.update {
                        it.copy(
                            loads = loads,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

}

data class LoadUiState(
    val loads: List<Load> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)