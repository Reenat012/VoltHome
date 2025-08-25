package ru.mugalimov.volthome.ui.viewmodel

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ru.mugalimov.volthome.data.repository.RoomRepository
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.core.error.RoomNotFoundException
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomType
import java.util.Date

@HiltViewModel //viewModel будет управляться Hilt
// класс отвечает за управление состоянием экрана (например, списка комнат)
// и взаимодействие с данными через репозиторий.
class RoomViewModel @Inject constructor( // @Inject constructor помечает конструктор как доступный для внедрения зависимостей
    private val roomRepository: RoomRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    //приватное состояние, хранящее данные для UI (список комнат, загрузка, ошибки).
    // Используется mutableStateOf (из Jetpack Compose) для реактивного обновления UI.
    private val _uiState = MutableStateFlow(RoomUiState())

    //публичное свойство, предоставляющее доступ к _uiState
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    // Получаем готовый список комнат для выпадающего меню
    private val _defaultRooms = MutableStateFlow<List<DefaultRoom>>(emptyList())
    val defaultRooms: StateFlow<List<DefaultRoom>> = _defaultRooms.asStateFlow()

    private val _deviceCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val deviceCounts: StateFlow<Map<Long, Int>> = _deviceCounts.asStateFlow()

    //Блок init вызывается при создании ViewModel.
    //Здесь запускается метод observeRooms(),
    //который начинает наблюдать (подписывается) за изменениями в списке комнат.
    init {
        observeRooms()
        loadDefaultRooms()

    }

    //наблюдение за данными
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
                } // Обрабатываем ошибки
                .collect { rooms ->
                    _uiState.update {
                        it.copy(
                            rooms = rooms,
                            isLoading = false,
                            error = null
                        )
                    }

                    // считаем количества устройств по комнатам
                    // (если хочешь — сделай через async/awaitAll, но так тоже ок)
                    val counts = rooms.associate { room ->
                        val cnt = deviceRepository.getAllDevicesByRoomId(room.id).size
                        room.id to cnt
                    }
                    _deviceCounts.value = counts
                } // Обновляем список комнат
        }
    }

    //добавление комнаты
    fun addRoom(name: String, roomType: RoomType) {
        viewModelScope.launch {
            executeOperation {
                try {
                    _uiState.update {
                        it.copy(isLoading = true)
                    }
                    validateName(name) // Проверяем валидность имени
                    val newRoom = Room(name = name, createdAt = Date(), roomType = roomType)
                    Log.d("addRoom VM", "${roomType}")

                    roomRepository.addRoom(newRoom) //добавляем комнату через репозиторий
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

    //удаление комнаты
    fun deleteRoom(roomId: Long) {
        viewModelScope.launch {
            executeOperation {
                roomRepository.deleteRoom(roomId)
            }
        }
    }

    // Обновляем Room с devices внутри
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
                Log.d(TAG, "${defaultRooms.value}")
            } catch (e: Exception) {
                Log.e("LOAD_ERROR", "Error loading rooms", e)
            }
        }
    }

    /**
     * Выполняет операцию с обработкой состояния загрузки и ошибок.
     * @param block Блок кода, который нужно выполнить.
     */
    private suspend fun <T> executeOperation(
        block: suspend () -> T
    ) {
        startLoading() // Начинаем загрузку
        try {
            block() // Выполняем операцию
            clearError() // Очищаем ошибки, если операция успешна
        } catch (e: Exception) {
            handleError(e) // Обрабатываем ошибку
        } finally {
            stopLoading() // Завершаем загрузку
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank()) {
            throw IllegalArgumentException("Имя комнаты не может быть пустым")
        }
    }

    //обновляем список комнат в состоянии UI
    private fun updateRooms(rooms: List<Room>) {
        _uiState.update { it.copy(rooms = rooms, error = null) }
    }

    //обрабатываем ошибку
    private fun handleError(e: Throwable) {
        _uiState.update { it.copy(error = e) }
    }

    //начинаем загрузку и обновляем состояние UI
    private fun startLoading() {
        _uiState.update { it.copy(isLoading = true) }
    }

    //завершаем загрузку и обновляем состояние UI
    private fun stopLoading() {
        _uiState.update { it.copy(isLoading = false) }
    }

    //сброс ошибки в состоянии UI
    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

//класс описывает состояние UI
data class RoomUiState(
    val rooms: List<Room> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)