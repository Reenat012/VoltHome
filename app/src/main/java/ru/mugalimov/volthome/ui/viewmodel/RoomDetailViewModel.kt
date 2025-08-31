package ru.mugalimov.volthome.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.domain.use_case.AddDevicesToRoomUseCase
import ru.mugalimov.volthome.domain.use_case.DeleteDevicesUseCase
import java.util.Date

/**
 * Детальная VM комнаты:
 * - Загружает данные комнаты и наблюдает устройства этой комнаты
 * - Пакетно добавляет выбранные устройства (multi-select + qty)
 * - Поддерживает Undo для дозагрузки устройств
 * - Сохраняет классический одиночный addDevice(...) для совместимости
 */
@HiltViewModel
class RoomDetailViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val deviceRepository: DeviceRepository,
    private val addDevicesToRoomUseCase: AddDevicesToRoomUseCase,
    private val deleteDevicesUseCase: DeleteDevicesUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // --------------------------------------
    // Input arguments
    // --------------------------------------
    private val _roomId: Long = savedStateHandle.get<Long>("roomId") ?: 0L
    val roomId: Long get() = _roomId

    // --------------------------------------
    // UI state
    // --------------------------------------
    private val _room = MutableStateFlow<Room?>(null)
    val room: StateFlow<Room?> = _room.asStateFlow()

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    // Каталог/пресеты устройств для листа «Все устройства»
    private val _defaultDevices = MutableStateFlow<List<DefaultDevice>>(emptyList())
    val defaultDevices: StateFlow<List<DefaultDevice>> = _defaultDevices.asStateFlow()

    // Одноразовые события для UI: снекбары/Undo/ошибки
    private val _actions = MutableSharedFlow<RoomDetailAction>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val actions: SharedFlow<RoomDetailAction> = _actions

    init {
        if (_roomId == 0L) {
            Log.e("RoomDetailViewModel", "roomId не передан или равен 0")
        }
        loadRoom()
        observeDevices()
        loadDefaultDevices()
    }

    // --------------------------------------
    // Data loading
    // --------------------------------------

    private fun loadRoom() {
        viewModelScope.launch {
            try {
                _room.value = roomRepository.getRoomById(roomId)
            } catch (_: Throwable) {
                _room.value = null
            }
        }
    }

    private fun observeDevices() {
        viewModelScope.launch {
            deviceRepository.observeDevicesByIdRoom(roomId)
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e) } }
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

    private fun loadDefaultDevices() {
        viewModelScope.launch {
            deviceRepository.getDefaultDevices()
                .catch { /* заглушка, каталог не критичен */ }
                .collect { list -> _defaultDevices.value = list }
        }
    }

    // --------------------------------------
    // New flow: пакетное добавление устройств + Undo
    // --------------------------------------

    /**
     * Добавляет в текущую комнату набор устройств (multi-select + qty).
     * Вызывать после подтверждения в bottom sheet «Все устройства».
     */
    fun addDevicesToCurrentRoom(selected: List<Pair<DefaultDevice, Int>>) {
        viewModelScope.launch {
            startLoading()
            try {
                val reqs: List<DeviceCreateRequest> = selected
                    .filter { it.second > 0 }
                    .map { (d, qty) -> d.toCreateRequest(qty) }

                if (reqs.isEmpty()) {
                    _actions.emit(RoomDetailAction.UserMessage("Выберите устройства"))
                    stopLoading()
                    return@launch
                }

                val insertedIds = addDevicesToRoomUseCase(roomId, reqs)
                _actions.emit(RoomDetailAction.DevicesAdded(roomId, insertedIds))
                clearError()
            } catch (t: Throwable) {
                _actions.emit(RoomDetailAction.Error(t))
                handleError(t)
            } finally {
                stopLoading()
            }
        }
    }

    /**
     * Undo для дозагрузки: удаляет только что добавленные устройства по их id.
     */
    fun undoAddDevices(deviceIds: List<Long>) {
        viewModelScope.launch {
            try {
                deleteDevicesUseCase(deviceIds)
            } catch (_: Throwable) {
                // ошибку отката можно тихо игнорировать, чтобы не раздражать пользователя
            }
        }
    }

    // --------------------------------------
    // Classic single-add (оставляем для совместимости)
    // --------------------------------------

    @Deprecated("Используйте addDevicesToCurrentRoom() с мультивыбором и qty")
    fun addDevice(
        name: String,
        power: Int,
        voltage: Voltage,
        demandRatio: Double,
        roomId: Long,
        deviceType: DeviceType,
        powerFactor: Double,
        hasMotor: Boolean,
        requiresDedicatedCircuit: Boolean
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                // простая валидация (оставил как у вас)
                validateName(name)
                validatePower(power)
                validateVoltage(voltage)
                validateDemandRatio(demandRatio)

                deviceRepository.addDevice(
                    Device(
                        name = name,
                        power = power,
                        voltage = voltage,
                        demandRatio = demandRatio,
                        roomId = this@RoomDetailViewModel.roomId,
                        deviceType = deviceType,
                        powerFactor = powerFactor,
                        hasMotor = hasMotor,
                        requiresDedicatedCircuit = requiresDedicatedCircuit
                    )
                )

                clearError()
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

//    // Удаление одного устройства (как и было)
//    fun deleteDevice(deviceId: Long) {
//        viewModelScope.launch {
//            executeOperation { deviceRepository.deleteDevice(deviceId) }
//        }
//    }

    fun deleteDevice(deviceId: Long) {
        viewModelScope.launch {
            deleteDevicesUseCase(listOf(deviceId))
        }
    }

    // --------------------------------------
    // Helpers / Validation / State
    // --------------------------------------

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
        if (name.isBlank()) throw IllegalArgumentException("Имя комнаты не может быть пустым")
    }

    private fun validatePower(power: Int) {
        if (power.toString().isBlank()) throw IllegalArgumentException("Поле мощности (Вт) не может быть пустым")
    }

    private fun validateVoltage(voltage: Voltage) {
        if (voltage.toString().isBlank()) throw IllegalArgumentException("Поле напряжения (В) не может быть пустым")
    }

    private fun validateDemandRatio(demandRatio: Double) {
        if (demandRatio.toString().isBlank()) throw IllegalArgumentException("Коэффициент спроса не может быть пустым")
    }

    private fun updateDevices(devices: List<Device>) {
        _uiState.update { it.copy(devices = devices, error = null) }
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

/** Состояние UI экрана комнаты */
data class DeviceUiState(
    val devices: List<Device> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)

/** Маппер пресета каталога в запрос на создание */
private fun DefaultDevice.toCreateRequest(qty: Int): DeviceCreateRequest =
    DeviceCreateRequest(
        title = this.name,
        type = this.deviceType,
        count = qty.coerceAtLeast(1),
        ratedPowerW = this.power,
        powerFactor = this.powerFactor,
        demandRatio = this.demandRatio,
        voltage = this.voltage
    )