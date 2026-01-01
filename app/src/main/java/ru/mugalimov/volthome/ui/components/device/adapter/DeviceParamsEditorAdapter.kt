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
     *
     * ВАЖНО: per-key state хранится внутри адаптера.
     */
    @Composable
    fun state(key: K, seed: DeviceParamsDraft): DeviceParamsEditorState<K>

    /**
     * RAW draft: ровно то, что вводил пользователь (без нормализации).
     * UI не должен сам нормализовать/валидировать — только через адаптер.
     */
    fun peekDraftRaw(key: K, seed: DeviceParamsDraft): DeviceParamsDraft

    /**
     * NORMALIZED draft: единая нормализация через валидатор (внутри адаптера).
     * UI не вызывает DeviceParamsValidator напрямую.
     */
    fun peekDraftNormalized(key: K, seed: DeviceParamsDraft): DeviceParamsDraft

    /**
     * Ошибки считаются на normalized draft (через валидатор) и учитывают план (free/pro).
     */
    fun peekErrors(key: K, seed: DeviceParamsDraft): DeviceParamsErrors

    /**
     * Expanded хранится per-key в адаптере (никаких remember в Lazy-item).
     */
    fun isExpanded(key: K): Boolean
    fun setExpanded(key: K, expanded: Boolean)
    fun toggleExpanded(key: K) = setExpanded(key, !isExpanded(key))
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
    val onRequiresSocketConnectionChange: (Boolean) -> Unit,

    // gating
    val locked: Boolean,
    val onLockedClick: () -> Unit
)