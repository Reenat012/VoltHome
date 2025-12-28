package ru.mugalimov.volthome.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsDraft
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsEditorAdapter
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsEditorState
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsErrors

class DeviceEditVmAdapter(
    override val isPro: Boolean,
    private val paywall: () -> Unit,
    private val vm: DeviceEditViewModel
) : DeviceParamsEditorAdapter<Long> {

    override fun onLockedClick() = paywall()

    @Composable
    override fun state(key: Long, seed: DeviceParamsDraft): DeviceParamsEditorState<Long> {
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

            isExpanded = true,
            onExpandedChange = { /* no-op */ },

            onNameChange = { raw ->
                vm.setName(raw)
            },
            onPowerTextChange = vm::setPowerText,

            onDeviceTypeChange = vm::setDeviceType,
            onPowerFactorTextChange = vm::setPowerFactorText,
            onDemandRatioTextChange = vm::setDemandRatioText,
            onVoltageTypeChange = vm::setVoltageType,

            onHasMotorChange = vm::setHasMotor,
            onRequiresDedicatedCircuitChange = vm::setRequiresDedicatedCircuit,
            onRequiresSocketConnectionChange = vm::setRequiresSocketConnection
        )
    }
}