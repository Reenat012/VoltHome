package ru.mugalimov.volthome.ui.viewmodel

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.domain.model.RoomWithLoad
import ru.mugalimov.volthome.data.repository.LoadsRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.use_case.CalcSumPowerDevices
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class LoadsScreenViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val loadRepository: LoadsRepository,
    private val loadDao: LoadDao,
    private val calcSumPowerDevices: CalcSumPowerDevices,
    savedStateHandle: SavedStateHandle // Конверт с запросом (ID комнаты)
) : ViewModel() {
//    // Наблюдение за нагрузками
//    private val _roomsWithLoads: Flow<List<RoomWithLoad>> = loadDao.getRoomsWithLoads()
//    val roomsWithLoads: Flow<List<RoomWithLoad>> = _roomsWithLoads

//
//
//    private val _roomId = savedStateHandle.get<Long>("roomId") ?: 0
//    val roomId = _roomId
//
//    private val _loads = MutableStateFlow<RoomWithLoad?>(null)
//    val loads: StateFlow<RoomWithLoad?> = _loads.asStateFlow()

    private val _uiState = MutableStateFlow(LoadUiState())
    val uiState: StateFlow<LoadUiState> = _uiState.asStateFlow()

    private val _sumLoad = MutableStateFlow(0)
    val sumLoad: StateFlow<Int> = _sumLoad.asStateFlow()

    // Получаем roomId из аргументов навигации
    private val _roomId = MutableStateFlow(0L)
    val roomId: StateFlow<Long> = _roomId.asStateFlow()


    init {
        Log.d(TAG, "Initializing with roomId: $roomId")
//        if (roomId.toInt() == 0) {
//            Log.d(TAG, "roomId = 0")
//        }

        observeLoads()
    }

    fun getRoomId(roomId: Long) {
        _roomId.value = roomId
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
                            loadsWithRoom = loads,
                            isLoading = false,
                            error = null
                        )

                    }

                }
        }
    }

    suspend fun calcSumLoad() {
        viewModelScope.launch {
            val updates = _uiState.value.loadsWithRoom.mapNotNull { roomWithLoad ->
                val roomId = roomWithLoad.room.id
                val newSumPower = calcSumPowerDevices.execute(roomId)

                roomWithLoad.load?.copy(sumPower = newSumPower) ?: run {
                    LoadEntity(
                        roomId = roomId,
                        sumPower = newSumPower,
                        name = "Auto",
                        current = 0.0,
                        createdAt = Date(),
                        countDevices = 0
                    ).also {
                        Log.d("DEBUG", "Creating new Load for room $roomId")
                    }
                }
            }

            loadDao.upsertAllLoads(updates) // Пакетное обновление
        }
    }

}

data class LoadUiState(
    val loadsWithRoom: List<RoomWithLoad> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)