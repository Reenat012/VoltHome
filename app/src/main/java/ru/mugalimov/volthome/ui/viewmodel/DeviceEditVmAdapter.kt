// File: volthome/ui/viewmodel/DeviceEditVmAdapter.kt
package ru.mugalimov.volthome.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsDraft
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsEditorAdapter
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsEditorState
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsErrors
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsValidator

class DeviceEditVmAdapter(
    override val isAllowed: Boolean,
    val paywall: () -> Unit,
    private val vm: DeviceEditViewModel
) : DeviceParamsEditorAdapter<Long> {

    override fun onLockedClick() = paywall()

    @Composable
    override fun state(key: Long, seed: DeviceParamsDraft): DeviceParamsEditorState<Long> {
        // seed игнорируем: источник правды в DeviceEdit — vm.ui
        val ui = vm.ui.collectAsState().value

        val draft = DeviceParamsDraft(
            name = ui.name,
            powerText = ui.powerText,
            deviceType = ui.deviceType,
            powerFactorText = ui.powerFactorText,
            demandRatioText = ui.demandRatioText,
            voltageType = ui.voltageType,
            hasMotor = ui.hasMotor,
            requiresDedicatedCircuit = ui.requiresDedicatedCircuit,
            requiresSocketConnection = ui.requiresSocketConnection
        )

        val errors = DeviceParamsErrors(
            nameError = ui.nameError,
            powerError = ui.powerError,
            powerFactorError = ui.powerFactorError,
            demandRatioError = ui.demandRatioError
        )

        return DeviceParamsEditorState(
            key = key,
            draft = draft,
            errors = errors,

            // в DeviceEdit это всегда "развёрнуто"
            isExpanded = true,
            onExpandedChange = { /* no-op */ },

            onNameChange = vm::setName,
            onPowerTextChange = vm::setPowerText,

            onDeviceTypeChange = vm::setDeviceType,
            onPowerFactorTextChange = vm::setPowerFactorText,
            onDemandRatioTextChange = vm::setDemandRatioText,
            onVoltageTypeChange = vm::setVoltageType,

            onHasMotorChange = vm::setHasMotor,
            onRequiresDedicatedCircuitChange = vm::setRequiresDedicatedCircuit,
            onRequiresSocketConnectionChange = vm::setRequiresSocketConnection,

            // gating
            locked = !isAllowed,
            onLockedClick = ::onLockedClick
        )
    }

    override fun peekDraftRaw(key: Long, seed: DeviceParamsDraft): DeviceParamsDraft {
        val ui = vm.ui.value
        return DeviceParamsDraft(
            name = ui.name,
            powerText = ui.powerText,
            deviceType = ui.deviceType,
            powerFactorText = ui.powerFactorText,
            demandRatioText = ui.demandRatioText,
            voltageType = ui.voltageType,
            hasMotor = ui.hasMotor,
            requiresDedicatedCircuit = ui.requiresDedicatedCircuit,
            requiresSocketConnection = ui.requiresSocketConnection
        )
    }

    override fun peekDraftNormalized(key: Long, seed: DeviceParamsDraft): DeviceParamsDraft {
        val raw = peekDraftRaw(key, seed)
        // нормализация внутри адаптера (НЕ в UI)
        return DeviceParamsValidator.validated(raw).normalized
    }

    override fun peekErrors(key: Long, seed: DeviceParamsDraft): DeviceParamsErrors {
        // В DeviceEdit ошибки уже вычислены во VM и лежат в ui
        val ui = vm.ui.value
        return DeviceParamsErrors(
            nameError = ui.nameError,
            powerError = ui.powerError,
            powerFactorError = ui.powerFactorError,
            demandRatioError = ui.demandRatioError
        )
    }

    override fun isExpanded(key: Long): Boolean = true

    override fun setExpanded(key: Long, expanded: Boolean) {
        // no-op: в DeviceEdit expansion не управляется
    }
}