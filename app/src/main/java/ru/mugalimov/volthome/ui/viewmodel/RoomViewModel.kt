package ru.mugalimov.volthome.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ru.mugalimov.volthome.repository.RoomRepository
import javax.inject.Inject
import androidx.compose.runtime.State
import androidx.compose.ui.util.fastCbrt
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.model.Room
import java.util.Date

@HiltViewModel //viewModel будет управляться Hilt
//класс отвечает за управление состоянием экрана (например, списка комнат)
// и взаимодействие с данными через репозиторий.
class RoomViewModel @Inject constructor( // @Inject constructor помечает конструктор как доступный для внедрения зависимостей
    private val roomRepository: RoomRepository
) : ViewModel() {

    //приватное состояние, хранящее данные для UI (список комнат, загрузка, ошибки).
    // Используется mutableStateOf (из Jetpack Compose) для реактивного обновления UI.
    private val _uiState = MutableStateFlow(RoomUiState())

    //публичное свойство, предоставляющее доступ к _uiState
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    //Блок init вызывается при создании ViewModel.
    //Здесь запускается метод observeRooms(),
    //который начинает наблюдать (подписывается) за изменениями в списке комнат.
    init {
        observeRooms()
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
                } // Обновляем список комнат
        }
    }

    //добавление комнаты
    fun addRoom(name: String) {
        viewModelScope.launch {
            executeOperation {
                try {
                    _uiState.update {
                        it.copy(isLoading = true)
                    }
                    validateName(name) // Проверяем валидность имени
                    val newRoom = Room(name = name, createdAt = Date())
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