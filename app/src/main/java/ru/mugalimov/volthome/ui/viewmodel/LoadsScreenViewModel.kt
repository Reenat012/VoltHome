package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.dao.LoadDao
import ru.mugalimov.volthome.model.RoomWithLoad
import ru.mugalimov.volthome.repository.LoadsRepository
import ru.mugalimov.volthome.repository.RoomRepository
import javax.inject.Inject

@HiltViewModel
class LoadsScreenViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val loadRepository: LoadsRepository,
    private val loadDao: LoadDao,
    savedStateHandle: SavedStateHandle // Конверт с запросом (ID комнаты)
) : ViewModel() {
//    // Наблюдение за нагрузками
    val roomsWithLoads: Flow<List<RoomWithLoad>> = loadDao.getRoomsWithLoads()
//
//
//    private val _roomId = savedStateHandle.get<Int>("roomId") ?: 0
//    val roomId = _roomId
//
//    private val _loads = MutableStateFlow<RoomWithLoad?>(null)
//    val loads: StateFlow<RoomWithLoad?> = _loads.asStateFlow()

    private val _uiState = MutableStateFlow(LoadUiState())
    val uiState: StateFlow<LoadUiState> = _uiState.asStateFlow()

    init {
        observeLoads()
    }

    private fun observeLoads() {
        viewModelScope.launch {
            roomRepository.getRoomsWithLoads()
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
                .collect { loads ->
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
    val loads: List<RoomWithLoad> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)