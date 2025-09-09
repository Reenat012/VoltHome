package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.core.validation.PowerValidator
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
        val error: String? = null,
        val powerError: String? = null,

        // readonly — для отображения в сетке 2×3
        val deviceTypeLabel: String = "",
        val powerFactorText: String = "",
        val demandRatioText: String = "",
        val voltageText: String = ""
    )

    enum class PowerUnit { W }

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
                unit = PowerUnit.W,
                deviceTypeLabel = device.deviceType.toString(),
                powerFactorText = device.powerFactor.toString(),
                demandRatioText = device.demandRatio.toString(),
                voltageText = device.voltage.value.toString()

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
            PowerUnit.W -> (raw * 1000.0).toInt().toString()      // kW -> W
        }
        _ui.value = s.copy(unit = unit, powerText = newText)
    }

    fun setPowerText(value: String) {
        // 1) Нормализация ввода:
        //   - запятая -> точка
        //   - удаляем все пробелы, включая NBSP (U+00A0) и узкий NBSP (U+202F)
        //   - фильтруем посторонние символы, оставляя цифры и одну точку
        val raw = value.replace(',', '.')
        val noSpaces = raw.replace(Regex("[\\s\\u00A0\\u202F]"), "")
        val cleaned = buildString(noSpaces.length) {
            var dotSeen = false
            for (ch in noSpaces) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' && !dotSeen -> {
                        append(ch); dotSeen = true
                    }

                    else -> Unit // игнорируем всё лишнее
                }
            }
        }

        // 2) Парсинг и валидация диапазона
        val asInt = cleaned.toDoubleOrNull()?.toInt()
        val err = when {
            cleaned.isEmpty() -> "Введите число > 0"
            asInt == null || asInt <= 0 -> "Введите число > 0"
            else -> ru.mugalimov.volthome.core.validation.PowerValidator.errorMessage(asInt)
        }

        _ui.value = _ui.value.copy(powerText = cleaned, powerError = err)
    }

    fun save(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val s = _ui.value
        val id = s.deviceId ?: return
        val name = s.name.trim()
        if (name.isEmpty()) {
            onError("Введите имя устройства"); return
        }
        val rawText = s.powerText.replace(',', '.').replace(Regex("[\\s\\u00A0\\u202F]"), "")
        val raw = rawText.toDoubleOrNull()
        if (raw == null || raw <= 0.0) {
            onError("Укажите корректную мощность"); return
        }

        val powerW = when (s.unit) {
            PowerUnit.W -> raw.toInt()
        }.coerceAtLeast(1)

        PowerValidator.errorMessage(powerW)?.let { msg ->
            _ui.value = _ui.value.copy(powerError = msg)
            onError(msg); return
        }

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