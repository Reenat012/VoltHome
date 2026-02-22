package ru.mugalimov.volthome.domain.telemetry

/**
 * ✅ Commit 1: событие корреляции операции добавления устройств.
 *
 * Важно:
 * - НЕ бизнес-модель
 * - используется только для логов/диагностики и корреляции UI visibility.
 */
data class CreateDeviceOpEvent(
    val opId: String,
    val projectIdRecorded: String,
    val roomId: Long,
    val insertedIds: List<Long>
)