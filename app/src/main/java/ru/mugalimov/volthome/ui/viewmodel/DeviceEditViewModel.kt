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
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.use_case.UpdateDeviceFieldsUseCase

@HiltViewModel
class DeviceEditViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val updateDeviceFields: UpdateDeviceFieldsUseCase
) : ViewModel() {

    data class UiState(
        val deviceId: Long? = null,
        val name: String = "",
        val powerText: String = "",
        val unit: PowerUnit = PowerUnit.W,
        val isSaving: Boolean = false,
        val error: String? = null,
        val powerError: String? = null,

        // readonly / editable in PRO
        val deviceTypeLabel: String = "",
        val powerFactorText: String = "",
        val demandRatioText: String = "",
        val voltageText: String = "",

        // validation for advanced
        val powerFactorError: String? = null,
        val demandRatioError: String? = null
    )

    enum class PowerUnit { W }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var isPro: Boolean = false
    fun setPlan(isPro: Boolean) { this.isPro = isPro }

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
                voltageText = formatVoltage(device.voltage)
            )
        }
    }

    fun setName(value: String) {
        _ui.value = _ui.value.copy(name = value.take(80))
    }

    fun setPowerText(value: String) {
        val raw = value.replace(',', '.')
        val noSpaces = raw.replace(Regex("[\\s\\u00A0\\u202F]"), "")
        val cleaned = buildString(noSpaces.length) {
            var dotSeen = false
            for (ch in noSpaces) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' && !dotSeen -> { append(ch); dotSeen = true }
                    else -> Unit
                }
            }
        }

        val asInt = cleaned.toDoubleOrNull()?.toInt()
        val err = when {
            cleaned.isEmpty() -> "Введите число > 0"
            asInt == null || asInt <= 0 -> "Введите число > 0"
            else -> PowerValidator.errorMessage(asInt)
        }

        _ui.value = _ui.value.copy(powerText = cleaned, powerError = err)
    }

    fun setPowerFactorText(value: String) {
        if (!isPro) return
        val normalized = value.trim().replace(',', '.')
        val err = validateRatio(normalized)
        _ui.value = _ui.value.copy(powerFactorText = normalized, powerFactorError = err)
    }

    fun setDemandRatioText(value: String) {
        if (!isPro) return
        val normalized = value.trim().replace(',', '.')
        val err = validateRatio(normalized)
        _ui.value = _ui.value.copy(demandRatioText = normalized, demandRatioError = err)
    }

    // ✅ Напряжение — только пресеты 220/380
    fun setVoltagePreset220() {
        if (!isPro) return
        _ui.value = _ui.value.copy(voltageText = "220 В (1ф)")
    }

    fun setVoltagePreset380() {
        if (!isPro) return
        _ui.value = _ui.value.copy(voltageText = "380 В (3ф)")
    }

    fun save(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val s = _ui.value
        val id = s.deviceId ?: return

        val name = s.name.trim()
        if (name.isEmpty()) { onError("Введите имя устройства"); return }

        val rawText = s.powerText
            .replace(',', '.')
            .replace(Regex("[\\s\\u00A0\\u202F]"), "")
        val raw = rawText.toDoubleOrNull()
        if (raw == null || raw <= 0.0) { onError("Укажите корректную мощность"); return }

        val powerW = raw.toInt().coerceAtLeast(1)
        PowerValidator.errorMessage(powerW)?.let { msg ->
            _ui.value = _ui.value.copy(powerError = msg)
            onError(msg); return
        }

        // advanced только для PRO
        val pf = if (isPro) s.powerFactorText.trim().replace(',', '.').toDoubleOrNull() else null
        val dr = if (isPro) s.demandRatioText.trim().replace(',', '.').toDoubleOrNull() else null

        if (isPro) {
            validateRatio(pf)?.let { onError(it); return }
            validateRatio(dr)?.let { onError(it); return }
        }

        val voltagePreset = if (isPro) parseVoltagePreset(s.voltageText) else null

        viewModelScope.launch {
            try {
                _ui.value = s.copy(isSaving = true, error = null)

                updateDeviceFields(
                    deviceId = id,
                    newName = name,
                    newPowerW = powerW,
                    newPowerFactor = pf,
                    newDemandRatio = dr,
                    newVoltageValue = voltagePreset?.value,
                    newVoltageType = voltagePreset?.type
                )

                _ui.value = s.copy(isSaving = false)
                onSuccess()
            } catch (t: Throwable) {
                _ui.value = s.copy(isSaving = false, error = t.message)
                onError(t.message ?: "Ошибка сохранения")
            }
        }
    }

    private fun validateRatio(v: String): String? {
        val d = v.toDoubleOrNull() ?: return "Введите число"
        return validateRatio(d)
    }

    private fun validateRatio(v: Double?): String? {
        if (v == null) return "Введите число"
        if (v < 0.1 || v > 1.0) return "Допустимо 0.1 — 1.0"
        return null
    }

    private data class VoltagePreset(val value: Int, val type: VoltageType)

    private fun parseVoltagePreset(text: String): VoltagePreset {
        // Мы сами генерим эти строки, так что это надёжно.
        return when {
            text.contains("380") -> VoltagePreset(380, VoltageType.AC_3PHASE)
            else -> VoltagePreset(220, VoltageType.AC_1PHASE)
        }
    }

    private fun formatVoltage(v: Voltage): String {
        return when (v.type) {
            VoltageType.AC_1PHASE -> "${v.value} В (1ф)"
            VoltageType.AC_3PHASE -> "${v.value} В (3ф)"
            VoltageType.DC -> "${v.value} В (DC)"
        }
    }
}