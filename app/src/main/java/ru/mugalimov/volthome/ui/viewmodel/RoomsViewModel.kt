package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest
import ru.mugalimov.volthome.data.repository.DeviceRepository   // твой репозиторий с пресетами
import ru.mugalimov.volthome.domain.model.DefaultDevice        // если у тебя так называется
import ru.mugalimov.volthome.domain.use_case.AddDevicesToRoomUseCase
import ru.mugalimov.volthome.domain.use_case.CreateRoomWithDevicesUseCase
import ru.mugalimov.volthome.domain.use_case.DeleteDevicesUseCase
import ru.mugalimov.volthome.domain.use_case.DeleteRoomUseCase

@HiltViewModel
class RoomsViewModel @Inject constructor(
    private val createRoom: CreateRoomWithDevicesUseCase,
    private val addDevices: AddDevicesToRoomUseCase,
    private val deleteRoom: DeleteRoomUseCase,
    private val deleteDevices: DeleteDevicesUseCase,
    deviceRepository: DeviceRepository
) : ViewModel() {

    /** Пресеты/каталог для листов выбора в UI */
    val defaultDevices = deviceRepository.getDefaultDevices()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Одноразовые события в UI: навигация, снекбары, undo */
    private val _actions = MutableSharedFlow<RoomsAction>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val actions: SharedFlow<RoomsAction> = _actions

    /** Простой UI state для кнопок (можно расширить при желании) */
    private val _isBusy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    /**
     * Создать комнату с устройствами за один вызов.
     * @param selected пары (DefaultDevice, qty) из листа с мультивыбором.
     */
    fun createRoomWithDevices(
        name: String,
        roomType: RoomType,
        selected: List<Pair<DefaultDevice, Int>>
    ) {
        // 1) Санитизация имени
        val safeName = name
            .replace("\n", " ")
            .replace("\r", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(40)

        if (safeName.isBlank()) {
            emitUserMessage("Введите название комнаты")
            return
        }
        if (_isBusy.value) return
        _isBusy.value = true

        viewModelScope.launch {
            try {
                val req = RoomCreateRequest(
                    name = safeName,                 // 2) Используем безопасное имя
                    roomType = roomType,
                    devices = selected
                        .filter { it.second > 0 }
                        .map { (d, qty) -> d.toCreateRequest(qty) }
                )
                val result = createRoom(req)
                _actions.emit(RoomsAction.RoomCreated(result.roomId, result.deviceIds))
            } catch (e: IllegalArgumentException) {
                emitUserMessage(e.message ?: "Ошибка: проверьте данные")
            } catch (t: Throwable) {
                _actions.emit(RoomsAction.Error(t))
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Добавить в уже существующую комнату набор устройств.
     */
    fun addDevicesToRoom(
        roomId: Long,
        selected: List<Pair<DefaultDevice, Int>>
    ) {
        if (_isBusy.value) return
        _isBusy.value = true

        viewModelScope.launch {
            try {
                val reqs = selected
                    .filter { it.second > 0 }
                    .map { (d, qty) -> d.toCreateRequest(qty) }

                if (reqs.isEmpty()) {
                    emitUserMessage("Нечего добавлять: выберите устройства")
                    _isBusy.value = false
                    return@launch
                }

                val ids = addDevices(roomId, reqs)
                _actions.emit(RoomsAction.DevicesAdded(roomId, ids))
            } catch (t: Throwable) {
                _actions.emit(RoomsAction.Error(t))
            } finally {
                _isBusy.value = false
            }
        }
    }

    /** Undo для комнаты */
    fun undoCreateRoom(roomId: Long) {
        viewModelScope.launch {
            try {
                deleteRoom(roomId)
            } catch (_: Throwable) {}
        }
    }

    /** Undo для дозагрузки устройств */
    fun undoAddDevices(deviceIds: List<Long>) {
        viewModelScope.launch {
            try {
                deleteDevices(deviceIds)
            } catch (_: Throwable) {}
        }
    }

    private fun emitUserMessage(msg: String) {
        viewModelScope.launch { _actions.emit(RoomsAction.UserMessage(msg)) }
    }
}

/** Маппер из DefaultDevice в DeviceCreateRequest */
private fun DefaultDevice.toCreateRequest(qty: Int): DeviceCreateRequest =
    DeviceCreateRequest(
        title = this.name,                  // как называется устройство в каталоге
        type = this.deviceType,             // DeviceType
        count = qty.coerceAtLeast(1),
        ratedPowerW = this.power,           // Int
        powerFactor = this.powerFactor,     // Double?
        demandRatio = this.demandRatio,     // Double?
        voltage = this.voltage              // Voltage
    )