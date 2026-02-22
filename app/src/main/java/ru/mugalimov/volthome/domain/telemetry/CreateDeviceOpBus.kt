package ru.mugalimov.volthome.domain.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ✅ Commit 1 (вариант B): шина корреляции add-devices через StateFlow.
 *
 * Это НЕ "event bus", это "последний снимок операции".
 * Плюсы:
 * - новая VM гарантированно увидит последний opId (если он ещё актуален)
 * - без replay/буферов/гонок подписки
 *
 * Минус:
 * - липкость, поэтому есть clear().
 */
class CreateDeviceOpBus {

    // null = "нет активной операции"
    private val _state = MutableStateFlow<CreateDeviceOpEvent?>(null)

    val state: StateFlow<CreateDeviceOpEvent?> = _state.asStateFlow()

    fun publish(event: CreateDeviceOpEvent) {
        _state.value = event
    }

    /**
     * Сброс состояния, чтобы новая VM не ловила старый opId.
     * Делай clear() после того, как UI дошёл до Success (или через небольшой таймаут).
     */
    fun clear() {
        _state.value = null
    }
}