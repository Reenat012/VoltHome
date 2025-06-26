package ru.mugalimov.volthome.ui.viewmodel

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.domain.model.RoomWithLoad
import ru.mugalimov.volthome.data.repository.LoadsRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.LoadListItem
import ru.mugalimov.volthome.domain.model.TotalLoad
import ru.mugalimov.volthome.domain.use_case.CalcLoads
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class LoadsScreenViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val loadRepository: LoadsRepository,
    private val deviceRepository: DeviceRepository,
    private val calcLoads: CalcLoads
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoadUiState())
    val uiState: StateFlow<LoadUiState> = _uiState.asStateFlow()

    // Получаем roomId из аргументов навигации
    private val _roomId = MutableStateFlow(0L)
    val roomId: StateFlow<Long> = _roomId.asStateFlow()

    // Добавляем флаг для принудительного обновления
    private val _refreshTrigger = MutableStateFlow(0)


    init {
        observeRoomsWithLoads()
    }

    fun refresh() {
        viewModelScope.launch {
            calcLoad()
        }
    }

    private fun observeRoomsWithLoads() {
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
                .collect { roomsWithLoads ->
                    // Формируем комбинированный список элементов
                    val items = buildList {
                        // Добавляем TotalLoad только если есть комнаты
                        if (roomsWithLoads.isNotEmpty()) {
                            add(LoadListItem.Total(calculateTotalLoad(roomsWithLoads)))
                        }

                        // Добавляем все комнаты
                        roomsWithLoads.forEach {
                            add(LoadListItem.Room(it))
                        }
                    }

                    _uiState.update {
                        it.copy(
                            items = items,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    // Вычисление суммарных значений
    private fun calculateTotalLoad(rooms: List<RoomWithLoad>): TotalLoad {
        var totalCurrent = 0.0
        var totalPower = 0
        var totalDevices = 0

        rooms.forEach { room ->
            room.load?.let { load ->
                totalCurrent += load.currentRoom
                totalPower += load.powerRoom
                totalDevices += load.countDevices
            }
        }

        return TotalLoad(
            totalCurrent = totalCurrent,
            totalDevices = totalDevices,
            totalPower = totalPower
        )
    }

    private suspend fun calcLoad() {
        val rooms = roomRepository.getAllRoom()
        val updates = mutableListOf<LoadEntity>()

        rooms.forEach { room ->
            val devices = deviceRepository.getAllDevicesByRoomId(room.id)
            if (devices.isNotEmpty()) {
                val powerRoom = calcLoads.calPowerRoom(room.id)
                val currentRoom = calcLoads.calcCurrentRoom(room.id)
                val countDevices = devices.size

                // Проверяем существование нагрузки для комнаты или создаем новую
                val existLoads = loadRepository.getLoadForRoom(room.id)

                val loadEntity = existLoads?.copy(
                    powerRoom = powerRoom,
                    currentRoom = currentRoom,
                    countDevices = countDevices
                ) ?: LoadEntity(
                    roomId = room.id,
                    name = "Auto",
                    currentRoom = currentRoom,
                    powerRoom = powerRoom,
                    countDevices = countDevices,
                    createdAt = Date(),
                )
                updates.add(loadEntity)
            }
        }

        // Сохраняем обновленные нагрузки
        if (updates.isNotEmpty()) {
            loadRepository.upsertAllLoads(updates)

            // Принудительно запрашиваем обновление
            observeRoomsWithLoads()
        }


    }

}

data class LoadUiState(
    val items: List<LoadListItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)