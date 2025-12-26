package ru.mugalimov.volthome.ui.components.device.adapter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsValidator.normalizeDecimal
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsValidator.normalizeName
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsValidator.validate

/**
 * Для AddRoomSheet / DevicePickerSheet.
 * Хранит drafts/errors/expanded per key и отдаёт единый контракт в editor.
 *
 * ВАЖНО: создавать через remember { ... } на уровне sheet,
 * чтобы stateMap жил весь lifecycle sheet.
 */
class InMemoryDeviceParamsAdapter<K>(
    override val isPro: Boolean,
    private val paywall: () -> Unit
) : DeviceParamsEditorAdapter<K> {

    private val drafts = mutableStateMapOf<K, DeviceParamsDraft>()
    private val expanded = mutableStateMapOf<K, Boolean>()
    private val errors = mutableStateMapOf<K, DeviceParamsErrors>()

    override fun onLockedClick() = paywall()

    @Composable
    override fun state(key: K, seed: DeviceParamsDraft): DeviceParamsEditorState<K> {
        // draft per key (фиксируем seed, если ключ встречен впервые)
        val currentDraft = drafts.getOrPut(key) { seed }

        // expanded per key
        val isExpanded = expanded[key] ?: false

        // errors per key (если нет — считаем и кладём)
        val currentErrors = errors[key] ?: validate(currentDraft).also { errors[key] = it }

        fun update(block: (DeviceParamsDraft) -> DeviceParamsDraft) {
            val base = drafts[key] ?: seed
            val newDraft = block(base)
            drafts[key] = newDraft

            // пересчитываем ошибки и сохраняем в map
            errors[key] = validate(newDraft)
        }

        return DeviceParamsEditorState(
            key = key,
            draft = currentDraft,
            errors = currentErrors,

            isExpanded = isExpanded,
            onExpandedChange = { v -> expanded[key] = v },

            onNameChange = { new ->
                update { it.copy(name = normalizeName(new)) }
            },
            onPowerTextChange = { new ->
                update { it.copy(powerText = normalizeDecimal(new)) }
            },

            onDeviceTypeChange = { new: DeviceType ->
                update { it.copy(deviceType = new) }
            },
            onPowerFactorTextChange = { new ->
                update { it.copy(powerFactorText = normalizeDecimal(new)) }
            },
            onDemandRatioTextChange = { new ->
                update { it.copy(demandRatioText = normalizeDecimal(new)) }
            },
            onVoltageTypeChange = { new: VoltageType ->
                update { it.copy(voltageType = new) }
            },

            onHasMotorChange = { v ->
                update { it.copy(hasMotor = v) }
            },
            onRequiresDedicatedCircuitChange = { v ->
                update { it.copy(requiresDedicatedCircuit = v) }
            },
            onRequiresSocketConnectionChange = { v ->
                update { it.copy(requiresSocketConnection = v) }
            }
        )
    }

    /**
     * Возвращает текущий draft (если его ещё нет — фиксирует seed как draft).
     * Это нужно для buildRequests и для внешней логики без локальных remember/map.
     */
    fun peekDraft(key: K, seed: DeviceParamsDraft): DeviceParamsDraft {
        return drafts.getOrPut(key) { seed }
    }

    /**
     * Возвращает текущие ошибки (если их ещё нет — валидирует draft/seed и кеширует).
     * Критично для: enabled кнопок (Create/Add) без powerErrorMap/powerErrorCache.
     */
    fun peekErrors(key: K, seed: DeviceParamsDraft): DeviceParamsErrors {
        val d = drafts.getOrPut(key) { seed }
        return errors[key] ?: validate(d).also { errors[key] = it }
    }

    /**
     * Быстрая проверка: есть ли любые ошибки среди keys.
     * seedForKey должен возвращать "точку правды" seed для ключа.
     */
    fun hasAnyErrors(
        keys: Iterable<K>,
        seedForKey: (K) -> DeviceParamsDraft,
        predicate: (DeviceParamsErrors) -> Boolean = { e -> e.powerError != null }
    ): Boolean {
        for (k in keys) {
            val e = peekErrors(k, seedForKey(k))
            if (predicate(e)) return true
        }
        return false
    }

    /**
     * Старое имя оставил как алиас, чтобы не ломать вызовы.
     * Но лучше постепенно перейти на peekDraft/peekErrors.
     */
    fun snapshot(key: K, seed: DeviceParamsDraft): DeviceParamsDraft = peekDraft(key, seed)
}