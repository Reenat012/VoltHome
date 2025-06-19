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
import ru.mugalimov.volthome.domain.use_case.CalcLoads
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class LoadsScreenViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val loadRepository: LoadsRepository,
    private val deviceRepository: DeviceRepository,
    private val loadDao: LoadDao,
    private val calcLoads: CalcLoads,
    savedStateHandle: SavedStateHandle // Конверт с запросом (ID комнаты)
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoadUiState())
    val uiState: StateFlow<LoadUiState> = _uiState.asStateFlow()

    // Получаем roomId из аргументов навигации
    private val _roomId = MutableStateFlow(0L)
    val roomId: StateFlow<Long> = _roomId.asStateFlow()


    init {
        Log.d(TAG, "Initializing with roomId: $roomId")
//        if (roomId.toInt() == 0) {
//            Log.d(TAG, "roomId = 0")
//        }

        observeRoomsWithLoads()
    }

    fun refresh() {
        viewModelScope.launch {
            calcLoad()
        }
    }

    fun getRoomId(roomId: Long) {
        _roomId.value = roomId
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

    private suspend fun calcLoad() {
        val rooms = roomRepository.getAllRoom()
        val updates = mutableListOf<LoadEntity>()

        rooms.forEach { room ->
            val devices = deviceRepository.getAllDevicesByRoomId(room.id)
            if (devices.isNotEmpty()) {
                val powerRoom = devices.sumOf { (it.power * it.demandRatio).toInt() }
                val currentRoom = devices.sumOf { it.current }
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

//        viewModelScope.launch {
//            val updates = _uiState.value.loadsWithRoom.map { roomWithLoad ->
//                val roomId = roomWithLoad.room.id
//                val newPowerRoom = calcLoads.calPowerRoom(roomId)
//
//                val devicesByRoom = deviceRepository.getAllDevicesByRoomId(roomId)
//
//                // Если список устройств пуст, пропускаем обновление
//                if (devicesByRoom.isEmpty()) {
//                    Log.d(TAG, "Нет устройств в комнате с id $roomId")
//                    return@map roomWithLoad.load
//                }
//
//                val sumVoltage = devicesByRoom.sumOf {
//                    it.voltage.value
//                }
//
//                val sumCurrent = devicesByRoom.sumOf {
//                    it.current
//                }
//
//                val countDevices = devicesByRoom.size
//
//                roomWithLoad.load?.copy(
//                    powerRoom = newPowerRoom,
//                    currentRoom = sumCurrent,
//                    countDevices = countDevices) ?: run {
//                    LoadEntity(
//                        roomId = roomId,
//                        powerRoom = newPowerRoom,
//                        name = "Auto",
//                        currentRoom = sumCurrent,
//                        createdAt = Date(),
//                        countDevices = countDevices
//                    ).also {
//                        Log.d("DEBUG", "Creating new Load for room $roomId")
//                    }
//                }
//            }
//
//            loadDao.upsertAllLoads(updates) // Пакетное обновление
//
//            // Принудительно запрашиваем обновление
//            observeLoads()
        }
    }

}

data class LoadUiState(
    val loadsWithRoom: List<RoomWithLoad> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)