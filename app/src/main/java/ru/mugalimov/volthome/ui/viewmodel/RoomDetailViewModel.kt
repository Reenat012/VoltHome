package ru.mugalimov.volthome.ui.viewmodel

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.data.repository.RoomRepository
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.domain.model.DefaultDevice
import java.util.Date
import java.util.Random

@HiltViewModel
class RoomDetailViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val deviceRepository: DeviceRepository,
    savedStateHandle: SavedStateHandle // Конверт с запросом (ID комнаты)
) : ViewModel() {

    private val _roomId = savedStateHandle.get<Long>("roomId") ?: 0
    val roomId = _roomId

    //хранилище комнат
    private val _room = MutableStateFlow<Room?>(null)
    val room: StateFlow<Room?> = _room.asStateFlow()

    //приватное состояние, хранящее данные для UI (список устройств, загрузка, ошибки).
    // Используется mutableStateOf (из Jetpack Compose) для реактивного обновления UI.
    private val _uiState = MutableStateFlow(DeviceUiState())
    //публичное свойство, предоставляющее доступ к _uiState
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    // Получаем устройства из каталога
    private val _defaultDevices = MutableStateFlow<List<DefaultDevice>>(emptyList())
    val defaultDevices: StateFlow<List<DefaultDevice>> = _defaultDevices.asStateFlow()

    private val _error = MutableSharedFlow<String>() // Для одноразовых событий
    val error: SharedFlow<String> = _error

    init {
        if (roomId.toInt() == 0) {
            Log.e(TAG, "Ошибка: roomId не передан или равен 0")
            // Можно выбросить исключение или показать ошибку в UI
        }

        loadRooms()
        observeDevices()
        loadDefaultDevices()
    }

    fun loadDefaultDevices() {
        viewModelScope.launch {
            try {
                _defaultDevices.value = deviceRepository.getDefaultDevices()
                    .first() // Берем первый элемент Flow
            } catch (e: Exception) {
                Log.e("LOAD_ERROR", "Error loading devices", e)
            }
        }
    }

    private fun loadRooms() {
        viewModelScope.launch {
            try {
                //ищем комнату по roomId
                val foundRoom = roomRepository.getRoomById(roomId)

                //записываем комнату в хранилище
                _room.value = foundRoom
            } catch (e: Exception) {
                //если комната не найдена, оставляем хранилище пустым
                _room.value = null
            }
        }
    }


    //наблюдение за данными
    private fun observeDevices() {
        viewModelScope.launch {
            deviceRepository.observeDevicesByIdRoom(_roomId)
                //начинаем поиск
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                // Обрабатываем ошибки
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e
                        )
                    }
                }
                // Обновляем список комнат
                .collect { devices ->
                    _uiState.update {
                        it.copy(
                            devices = devices,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    //добавление комнаты
    fun addDevice(name: String, power: Int, voltage: Int, demandRatio: Double, roomId: Long) {
        viewModelScope.launch {
            //TODO удалить после отладки
            Log.d(TAG, "Заходим в метод")
            try {
                _uiState.update {

                    //TODO удалить после отладки
                    Log.d(TAG, "loading")

                    it.copy(isLoading = true)
                }

                // Проверяем валидность параметров
                validateName(name)
                validatePower(power)
                validateVoltage(voltage)
                validateDemandRatio(demandRatio)

                // Добавляем комнату через репозиторий
                deviceRepository.addDevice(
                    name = name,
                    power = power,
                    voltage = voltage,
                    demandRatio = demandRatio,
                    roomId = this@RoomDetailViewModel.roomId
                )
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

    // Удаление комнаты
    fun deleteDevice(deviceId: Long) {
        viewModelScope.launch {
            executeOperation {
                deviceRepository.deleteDevice(deviceId)
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

    private fun validatePower(power: Int) {
        if (power.toString().isBlank()) {
            throw IllegalArgumentException("Поле мощности (кВт) не может быть пустым")
        }
    }

    private fun validateVoltage(voltage: Int) {
        if (voltage.toString().isBlank()) {
            throw IllegalArgumentException("Поле напряжения (В) не может быть пустым")
        }
    }

    private fun validateDemandRatio(demandRatio: Double) {
        if (demandRatio.toString().isBlank()) {
            throw IllegalArgumentException("Поле коэффициента спроса (В) не может быть пустым")
        }
    }

    //обновляем список комнат в состоянии UI
    private fun updateDevices(devices: List<Device>) {
        _uiState.update { it.copy(devices = devices, error = null) }
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

// Класс описывает состояние UI
data class DeviceUiState(
    val devices: List<Device> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)
