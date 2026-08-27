package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.PlanCapabilities
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.use_case.UpdateDeviceFieldsUseCase
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsValidator
import javax.inject.Inject

@HiltViewModel
class DeviceEditViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val updateDeviceFields: UpdateDeviceFieldsUseCase
) : ViewModel() {

    data class UiState(
        val deviceId: Long? = null,
        val name: String = "",
        val powerText: String = "",
        val isSaving: Boolean = false,
        val error: String? = null,

        // validation (единый источник)
        val nameError: String? = null,
        val powerError: String? = null,
        val powerFactorError: String? = null,
        val demandRatioError: String? = null,

        // PRO values
        val deviceType: DeviceType = DeviceType.OTHER,
        val powerFactorText: String = "",
        val demandRatioText: String = "",
        val voltageType: VoltageType = VoltageType.AC_1PHASE,

        val hasMotor: Boolean = false,
        val requiresDedicatedCircuit: Boolean = false,
        val requiresSocketConnection: Boolean = true,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var capabilities: PlanCapabilities = PlanCapabilities(
        pdfExport = false,
        professionalReportSections = false,
        phaseDragAndDrop = false,
        extendedDeviceEditor = false,
        panelVisualization = false,
        unlimitedProjects = false
    )

    private val canUseExtendedEditor: Boolean
        get() = capabilities.extendedDeviceEditor

    private fun recompute(next: UiState): UiState {
        val raw = DeviceParamsValidator.toDraft(next)
        val res = DeviceParamsValidator.validated(raw)
        val e = DeviceParamsValidator.validateForPlan(res.normalized, canUseExtendedEditor)

        return next.copy(
            nameError = e.nameError,
            powerError = e.powerError,
            powerFactorError = e.powerFactorError,
            demandRatioError = e.demandRatioError
        )
    }

    fun setPlan(canUseExtendedEditor: Boolean) {
        // В этом коммите UI ещё передаёт canUseExtendedEditor.
        // Внутри VM переводим это в capabilities, чтобы далее VM жил на флагах прав.
        this.capabilities = if (canUseExtendedEditor) {
            PlanCapabilities(
                pdfExport = true,
                professionalReportSections = true,
                phaseDragAndDrop = true,
                extendedDeviceEditor = true,
                panelVisualization = true,
                unlimitedProjects = true
            )
        } else {
            PlanCapabilities(
                pdfExport = false,
                professionalReportSections = false,
                phaseDragAndDrop = false,
                extendedDeviceEditor = false,
                panelVisualization = false,
                unlimitedProjects = false
            )
        }
        _ui.value = recompute(_ui.value)
    }

    fun load(deviceId: Long) {
        viewModelScope.launch {
            val device = deviceRepository.getDeviceById(deviceId.toInt())
                ?: error("Устройство не найдено: $deviceId")

            val loaded = UiState(
                deviceId = device.id,
                name = device.name,
                powerText = device.power.toString(),
                deviceType = device.deviceType,
                powerFactorText = device.powerFactor.toString(),
                demandRatioText = device.demandRatio.toString(),
                voltageType = device.voltage.type,
                hasMotor = device.hasMotor,
                requiresDedicatedCircuit = device.requiresDedicatedCircuit,
                requiresSocketConnection = device.requiresSocketConnection
            )

            _ui.value = recompute(loaded)
        }
    }

    fun setName(value: String) {
        _ui.value = recompute(_ui.value.copy(name = value))
    }

    fun setPowerText(value: String) {
        _ui.value = recompute(_ui.value.copy(powerText = value))
    }

    /**
     * Anti-silence:
     * VM не должна "молча" игнорировать интенты.
     * Гейтинг PRO делается в UI через gate/adapter (locked tap -> adapter.onLockedClick()).
     */
    fun setDeviceType(value: DeviceType) {
        _ui.value = recompute(_ui.value.copy(deviceType = value))
    }

    fun setVoltageType(value: VoltageType) {
        // не PRO, а фича реально выключена
        if (value == VoltageType.DC) return
        _ui.value = recompute(_ui.value.copy(voltageType = value))
    }

    fun setHasMotor(value: Boolean) {
        _ui.value = recompute(_ui.value.copy(hasMotor = value))
    }

    fun setRequiresDedicatedCircuit(value: Boolean) {
        _ui.value = recompute(_ui.value.copy(requiresDedicatedCircuit = value))
    }

    fun setRequiresSocketConnection(value: Boolean) {
        _ui.value = recompute(_ui.value.copy(requiresSocketConnection = value))
    }

    fun setPowerFactorText(value: String) {
        _ui.value = recompute(_ui.value.copy(powerFactorText = value))
    }

    fun setDemandRatioText(value: String) {
        _ui.value = recompute(_ui.value.copy(demandRatioText = value))
    }

    fun save(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val s = _ui.value
        val id = s.deviceId ?: return

        val raw = DeviceParamsValidator.toDraft(s)
        val res = DeviceParamsValidator.validated(raw)
        val norm = res.normalized
        val e = DeviceParamsValidator.validateForPlan(norm, canUseExtendedEditor)

        val firstError =
            e.nameError
                ?: e.powerError
                ?: (if (canUseExtendedEditor) e.powerFactorError else null)
                ?: (if (canUseExtendedEditor) e.demandRatioError else null)

        if (firstError != null) {
            onError(firstError)
            return
        }

        // Парсим только из normalized (и только после отсутствия ошибок)
        val powerW = norm.powerText.toDouble().toInt()
        val name = norm.name
        val pf = if (canUseExtendedEditor) norm.powerFactorText.toDouble() else null
        val dr = if (canUseExtendedEditor) norm.demandRatioText.toDouble() else null

        viewModelScope.launch {
            try {
                _ui.value = s.copy(isSaving = true, error = null)

                updateDeviceFields(
                    deviceId = id,
                    newName = name,
                    newPowerW = powerW,

                    // PRO
                    newDeviceType = if (canUseExtendedEditor) s.deviceType else null,
                    newPowerFactor = pf,
                    newDemandRatio = dr,
                    newVoltageType = if (canUseExtendedEditor) s.voltageType else null,
                    newHasMotor = if (canUseExtendedEditor) s.hasMotor else null,
                    newRequiresDedicatedCircuit = if (canUseExtendedEditor) s.requiresDedicatedCircuit else null,
                    newRequiresSocketConnection = if (canUseExtendedEditor) s.requiresSocketConnection else null
                )

                _ui.value = s.copy(isSaving = false)
                onSuccess()
            } catch (t: Throwable) {
                _ui.value = s.copy(isSaving = false, error = t.message)
                onError(t.message ?: "Ошибка сохранения")
            }
        }
    }
}
