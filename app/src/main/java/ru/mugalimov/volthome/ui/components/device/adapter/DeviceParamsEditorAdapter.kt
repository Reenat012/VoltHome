package ru.mugalimov.volthome.ui.components.device.adapter

import androidx.compose.runtime.Composable
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.VoltageType

interface DeviceParamsEditorAdapter<K> {
    val isPro: Boolean

    /**
     * Единая точка входа для paywall.
     * Editor дергает это на любой locked click.
     */
    fun onLockedClick()

    /**
     * Возвращает состояние для DeviceParamsEditor.
     * seed — стартовое состояние (из DefaultDevice, Device, или VM ui).
     */
    @Composable
    fun state(key: K, seed: DeviceParamsDraft): DeviceParamsEditorState<K>
}

data class DeviceParamsEditorState<K>(
    val key: K,
    val draft: DeviceParamsDraft,
    val errors: DeviceParamsErrors,

    val isExpanded: Boolean,
    val onExpandedChange: (Boolean) -> Unit,

    val onNameChange: (String) -> Unit,
    val onPowerTextChange: (String) -> Unit,

    val onDeviceTypeChange: (DeviceType) -> Unit,
    val onPowerFactorTextChange: (String) -> Unit,
    val onDemandRatioTextChange: (String) -> Unit,
    val onVoltageTypeChange: (VoltageType) -> Unit,

    val onHasMotorChange: (Boolean) -> Unit,
    val onRequiresDedicatedCircuitChange: (Boolean) -> Unit,
    val onRequiresSocketConnectionChange: (Boolean) -> Unit
)