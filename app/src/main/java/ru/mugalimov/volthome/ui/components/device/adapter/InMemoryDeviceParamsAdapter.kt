// File: volthome/ui/components/device/adapter/InMemoryDeviceParamsAdapter.kt
package ru.mugalimov.volthome.ui.components.device.adapter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsValidator.validated

/**
 * Для AddRoomScreen / DevicePickerSheet / DeviceEditSheet.
 * Хранит drafts/errors/expanded per key и отдаёт единый контракт в editor.
 *
 * ЕДИНСТВЕННАЯ точка правды:
 *  - UI хранит RAW (как ввёл пользователь)
 *  - ошибки считаются по NORMALIZED (через валидатор)
 *  - UI может получить NORMALIZED draft без прямого вызова валидатора
 */
class InMemoryDeviceParamsAdapter<K>(
    override val isAllowed: Boolean,
    private val paywall: () -> Unit
) : DeviceParamsEditorAdapter<K> {

    private val drafts = mutableStateMapOf<K, DeviceParamsDraft>()
    private val expanded = mutableStateMapOf<K, Boolean>()
    private val errors = mutableStateMapOf<K, DeviceParamsErrors>()

    override fun onLockedClick() = paywall()

    /**
     * ЕДИНСТВЕННАЯ точка:
     * - сохраняем RAW draft (как ввёл пользователь)
     * - считаем errors по NORMALIZED (через валидатор)
     */
    private fun setDraft(key: K, newDraftRaw: DeviceParamsDraft) {
        val res = validated(newDraftRaw)
        drafts[key] = newDraftRaw
        errors[key] = DeviceParamsValidator.validateForPlan(res.normalized, isAllowed)
    }

    @Composable
    override fun state(key: K, seed: DeviceParamsDraft): DeviceParamsEditorState<K> {
        // Чтение composable не должно мутировать snapshot-state. Состояние появится
        // в картах только после первого пользовательского изменения или peek при сохранении.
        val currentDraft = drafts[key] ?: seed

        val isExpanded = expanded[key] ?: false

        val currentErrors = errors[key]
            ?: DeviceParamsValidator.validateForPlan(validated(currentDraft).normalized, isAllowed)

        fun update(block: (DeviceParamsDraft) -> DeviceParamsDraft) {
            val base = drafts[key] ?: seed
            val newDraft = block(base)
            setDraft(key, newDraft)
        }

        return DeviceParamsEditorState(
            key = key,
            draft = currentDraft,
            errors = currentErrors,

            isExpanded = isExpanded,
            onExpandedChange = { v -> expanded[key] = v },

            onNameChange = { new -> update { it.copy(name = new) } },
            onPowerTextChange = { new -> update { it.copy(powerText = new) } },

            onDeviceTypeChange = { new: DeviceType -> update { it.copy(deviceType = new) } },
            onPowerFactorTextChange = { new -> update { it.copy(powerFactorText = new) } },
            onDemandRatioTextChange = { new -> update { it.copy(demandRatioText = new) } },
            onVoltageTypeChange = { new: VoltageType -> update { it.copy(voltageType = new) } },

            onHasMotorChange = { v -> update { it.copy(hasMotor = v) } },
            onRequiresDedicatedCircuitChange = { v -> update { it.copy(requiresDedicatedCircuit = v) } },
            onRequiresSocketConnectionChange = { v -> update { it.copy(requiresSocketConnection = v) } },

            // gating
            locked = !isAllowed,
            onLockedClick = ::onLockedClick
        )
    }

    override fun peekDraftRaw(key: K, seed: DeviceParamsDraft): DeviceParamsDraft {
        return drafts[key] ?: run {
            setDraft(key, seed)
            drafts.getValue(key)
        }
    }

    /**
     * Возвращает NORMALIZED draft (через общий валидатор), чтобы UI не делал локальную нормализацию.
     */
    override fun peekDraftNormalized(key: K, seed: DeviceParamsDraft): DeviceParamsDraft {
        val raw = peekDraftRaw(key, seed)
        return validated(raw).normalized
    }

    override fun peekErrors(key: K, seed: DeviceParamsDraft): DeviceParamsErrors {
        if (!errors.containsKey(key)) {
            val d = drafts[key] ?: seed
            setDraft(key, d)
        }
        return errors.getValue(key)
    }

    override fun isExpanded(key: K): Boolean = expanded[key] ?: false

    override fun setExpanded(key: K, expanded: Boolean) {
        this.expanded[key] = expanded
    }

    fun hasAnyErrors(
        keys: Iterable<K>,
        seedForKey: (K) -> DeviceParamsDraft,
        predicate: (DeviceParamsErrors) -> Boolean = { e ->
            e.nameError != null ||
                    e.powerError != null ||
                    e.powerFactorError != null ||
                    e.demandRatioError != null
        }
    ): Boolean {
        for (k in keys) {
            val e = peekErrors(k, seedForKey(k))
            if (predicate(e)) return true
        }
        return false
    }
}
