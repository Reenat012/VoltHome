package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.domain.use_case.UpdateDeviceFieldsUseCase

@HiltViewModel
class DeviceEditViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val updateDeviceFields: UpdateDeviceFieldsUseCase
) : ViewModel() {

    data class UiState(
        val deviceId: Long? = null,
        val name: String = "",
        val powerText: String = "",   // текст в текущих единицах
        val unit: PowerUnit = PowerUnit.W,
        val isSaving: Boolean = false,
        val error: String? = null
    )

    enum class PowerUnit { W, kW }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun load(deviceId: Long) {
        viewModelScope.launch {
            val device = deviceRepository.getDeviceById(deviceId.toInt())
                ?: error("Устройство не найдено: $deviceId")
            _ui.value = UiState(
                deviceId = device.id,
                name = device.name,
                powerText = device.power.toString(),
                unit = PowerUnit.W
            )
        }
    }

    fun setName(value: String) {
        _ui.value = _ui.value.copy(name = value.take(80))
    }

    fun setUnit(unit: PowerUnit) {
        val s = _ui.value
        if (s.powerText.isBlank()) {
            _ui.value = s.copy(unit = unit); return
        }
        val raw = s.powerText.replace(',', '.').toDoubleOrNull() ?: run {
            _ui.value = s.copy(unit = unit); return
        }
        val newText = when (unit) {
            PowerUnit.W  -> (raw * 1000.0).toInt().toString()      // kW -> W
            PowerUnit.kW -> "%.3f".format(raw / 1000.0)            // W  -> kW
        }
        _ui.value = s.copy(unit = unit, powerText = newText)
    }

    fun setPowerText(value: String) {
        _ui.value = _ui.value.copy(powerText = value.replace(',', '.'))
    }

    fun save(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val s = _ui.value
        val id = s.deviceId ?: return
        val name = s.name.trim()
        if (name.isEmpty()) {
            onError("Введите имя устройства"); return
        }
        val raw = s.powerText.replace(',', '.').toDoubleOrNull()
        if (raw == null || raw <= 0.0) {
            onError("Укажите корректную мощность"); return
        }
        val powerW = when (s.unit) {
            PowerUnit.W  -> raw.toInt()
            PowerUnit.kW -> (raw * 1000.0).toInt()
        }.coerceAtLeast(1)

        viewModelScope.launch {
            try {
                _ui.value = s.copy(isSaving = true, error = null)
                updateDeviceFields(id, name, powerW)
                _ui.value = s.copy(isSaving = false)
                onSuccess()
            } catch (t: Throwable) {
                _ui.value = s.copy(isSaving = false, error = t.message)
                onError(t.message ?: "Ошибка сохранения")
            }
        }
    }
}