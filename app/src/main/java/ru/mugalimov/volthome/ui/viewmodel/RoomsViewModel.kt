package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest
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
    private val preferencesRepository: PreferencesRepository,
    deviceRepository: DeviceRepository
) : ViewModel() {

    /** Пресеты/каталог для листов выбора в UI */
    val defaultDevices: StateFlow<List<DefaultDevice>> = deviceRepository.getDefaultDevices()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Одноразовые события в UI: навигация, снекбары, undo */
    private val _actions = MutableSharedFlow<RoomsAction>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val actions: SharedFlow<RoomsAction> = _actions

    /** Простой UI state для кнопок/лоадеров */
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // Текущее значение режима для UI (с дефолтом THREE)
    val phaseMode: StateFlow<PhaseMode> =
        preferencesRepository.phaseMode
            .stateIn(viewModelScope, SharingStarted.Lazily, PhaseMode.THREE)

    /**
     * ВАРИАНТ 1 (СОХРАНЁН ДЛЯ ОБРАТНОЙ СОВМЕСТИМОСТИ):
     * Создать комнату с устройствами из пары (DefaultDevice, qty).
     * Используется старым потоком — без кастомизации имени/мощности на шаге выбора.
     */
    fun createRoomWithDevices(
        name: String,
        roomType: RoomType,
        selected: List<Pair<DefaultDevice, Int>>
    ) {
        val safeName = sanitizeRoomName(name)
        if (safeName.isBlank()) {
            emitUserMessage("Введите название комнаты")
            return
        }
        if (_isBusy.value) return
        _isBusy.value = true

        viewModelScope.launch {
            try {
                val requests = selected
                    .filter { it.second > 0 }
                    .map { (d, qty) -> d.toCreateRequest(qty) }

                val req = RoomCreateRequest(
                    name = safeName,
                    roomType = roomType,
                    devices = requests
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
     * ВАРИАНТ 2 (НОВЫЙ ОСНОВНОЙ ДЛЯ «1-в-1» С ЭКРАНОМ «Комната»):
     * Создать комнату с кастомизированными устройствами.
     * Здесь devices уже содержат кастомные title и ratedPowerW (Вт) — как из DevicePickerSheet.
     */
    fun createRoomWithDevicesCustomized(
        name: String,
        roomType: RoomType,
        devices: List<DeviceCreateRequest>
    ) {
        val safeName = sanitizeRoomName(name)
        if (safeName.isBlank()) {
            emitUserMessage("Введите название комнаты")
            return
        }
        if (_isBusy.value) return
        _isBusy.value = true

        viewModelScope.launch {
            try {
                val filtered = devices.filter { it.count > 0 }
                if (filtered.isEmpty()) {
                    emitUserMessage("Нечего добавлять: выберите устройства")
                    _isBusy.value = false
                    return@launch
                }
                val req = RoomCreateRequest(
                    name = safeName,
                    roomType = roomType,
                    devices = filtered
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

    fun setPhaseMode(mode: PhaseMode) {
        viewModelScope.launch { preferencesRepository.setPhaseMode(mode) }
    }

    /**
     * ВАРИАНТ 1 (СОХРАНЁН): Добавить в комнату набор устройств по парам (DefaultDevice, qty).
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

    /**
     * ВАРИАНТ 2 (НОВЫЙ ОСНОВНОЙ): Добавить кастомизированные устройства (имя + мощность) в комнату.
     * Список devices формируется в DevicePickerSheet (точно так же, как на экране «Комната»).
     */
    fun addDevicesToRoomCustomized(
        roomId: Long,
        devices: List<DeviceCreateRequest>
    ) {
        if (_isBusy.value) return
        _isBusy.value = true

        viewModelScope.launch {
            try {
                val filtered = devices.filter { it.count > 0 }
                if (filtered.isEmpty()) {
                    emitUserMessage("Нечего добавлять: выберите устройства")
                    _isBusy.value = false
                    return@launch
                }
                val ids = addDevices(roomId, filtered)
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
            } catch (_: Throwable) { /* ignore */ }
        }
    }

    /** Undo для дозагрузки устройств */
    fun undoAddDevices(deviceIds: List<Long>) {
        viewModelScope.launch {
            try {
                deleteDevices(deviceIds)
            } catch (_: Throwable) { /* ignore */ }
        }
    }

    private fun emitUserMessage(msg: String) {
        viewModelScope.launch { _actions.emit(RoomsAction.UserMessage(msg)) }
    }

    private fun sanitizeRoomName(name: String): String =
        name.replace("\n", " ")
            .replace("\r", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(40)
}

