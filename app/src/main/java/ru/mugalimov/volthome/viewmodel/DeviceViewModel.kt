package ru.mugalimov.volthome.viewmodel

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
import ru.mugalimov.volthome.model.Device
import ru.mugalimov.volthome.repository.DeviceRepository
import javax.inject.Inject

//@HiltViewModel //viewModel будет управляться Hilt
////класс отвечает за управление состоянием экрана (например, списка комнат)
//// и взаимодействие с данными через репозиторий.
//class DeviceViewModel @Inject constructor( // @Inject constructor помечает конструктор как доступный для внедрения зависимостей
//    private val deviceRepository: DeviceRepository
//) : ViewModel() {
//
//    //приватное состояние, хранящее данные для UI (список устройств, загрузка, ошибки).
//    // Используется mutableStateOf (из Jetpack Compose) для реактивного обновления UI.
//    private val _uiState = MutableStateFlow(DeviceUiState())
//
//    //публичное свойство, предоставляющее доступ к _uiState
//    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()
//
//    //Блок init вызывается при создании ViewModel.
//    //Здесь запускается метод observeRooms(),
//    //который начинает наблюдать (подписывается) за изменениями в списке комнат.
//    init {
//        observeDevices()
//    }
//
//    //наблюдение за данными
//    private fun observeDevices() {
//        viewModelScope.launch {
//            deviceRepository.observeDevices()
//                .onStart { _uiState.update { it.copy(isLoading = true) } }
//                .catch { e ->
//                    _uiState.update {
//                        it.copy(
//                            isLoading = false,
//                            error = e
//                        )
//                    }
//                } // Обрабатываем ошибки
//                .collect { devices ->
//                    _uiState.update {
//                        it.copy(
//                            devices = devices,
//                            isLoading = false,
//                            error = null
//                        )
//                    }
//                } // Обновляем список комнат
//        }
//    }
//
//    //добавление комнаты
//    fun addDevice(name: String, power: Int, voltage: Int, demandRatio: Double) {
//        viewModelScope.launch {
//            executeOperation {
//                try {
//                    _uiState.update {
//                        it.copy(isLoading = true)
//                    }
//
//                    // Проверяем валидность параметров
//                    validateName(name)
//                    validatePower(power)
//                    validateVoltage(voltage)
//                    validateDemandRatio(demandRatio)
//
//                    // Добавляем комнату через репозиторий
//                    deviceRepository.addDevice(name, power, voltage, demandRatio)
//                } catch (e: Exception) {
//                    _uiState.update {
//                        it.copy(
//                            error = e,
//                            isLoading = false
//                        )
//                    }
//                }
//            }
//        }
//    }
//
//    // Удаление комнаты
//    fun deleteDevice(deviceId: Int) {
//        viewModelScope.launch {
//            executeOperation {
//                deviceRepository.deleteDevice(deviceId)
//            }
//        }
//    }
//
//    /**
//     * Выполняет операцию с обработкой состояния загрузки и ошибок.
//     * @param block Блок кода, который нужно выполнить.
//     */
//    private suspend fun <T> executeOperation(
//        block: suspend () -> T
//    ) {
//        startLoading() // Начинаем загрузку
//        try {
//            block() // Выполняем операцию
//            clearError() // Очищаем ошибки, если операция успешна
//        } catch (e: Exception) {
//            handleError(e) // Обрабатываем ошибку
//        } finally {
//            stopLoading() // Завершаем загрузку
//        }
//    }
//
//    private fun validateName(name: String) {
//        if (name.isBlank()) {
//            throw IllegalArgumentException("Имя комнаты не может быть пустым")
//        }
//    }
//
//    private fun validatePower(power: Int) {
//        if (power.toString().isBlank()) {
//            throw IllegalArgumentException("Поле мощности (кВт) не может быть пустым")
//        }
//    }
//
//    private fun validateVoltage(voltage: Int) {
//        if (voltage.toString().isBlank()) {
//            throw IllegalArgumentException("Поле напряжения (В) не может быть пустым")
//        }
//    }
//
//    private fun validateDemandRatio(demandRatio: Double) {
//        if (demandRatio.toString().isBlank()) {
//            throw IllegalArgumentException("Поле коэффициента спроса (В) не может быть пустым")
//        }
//    }
//
//    //обновляем список комнат в состоянии UI
//    private fun updateDevices(devices: List<Device>) {
//        _uiState.update { it.copy(devices = devices, error = null) }
//    }
//
//    //обрабатываем ошибку
//    private fun handleError(e: Throwable) {
//        _uiState.update { it.copy(error = e) }
//    }
//
//    //начинаем загрузку и обновляем состояние UI
//    private fun startLoading() {
//        _uiState.update { it.copy(isLoading = true) }
//    }
//
//    //завершаем загрузку и обновляем состояние UI
//    private fun stopLoading() {
//        _uiState.update { it.copy(isLoading = false) }
//    }
//
//    //сброс ошибки в состоянии UI
//    private fun clearError() {
//        _uiState.update { it.copy(error = null) }
//    }
//}
//
//// Класс описывает состояние UI
//data class DeviceUiStatee(
//    val devices: List<Device> = emptyList(),
//    val isLoading: Boolean = true,
//    val error: Throwable? = null
//)