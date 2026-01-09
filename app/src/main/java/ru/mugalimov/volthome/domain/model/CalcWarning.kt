package ru.mugalimov.volthome.domain.model

/**
 * Предупреждение по расчёту/данным (не ошибка выполнения, а риск/аномалия).
 * Должно отображаться в UI/PDF, а не уходить в Log.w.
 */
data class CalcWarning(
    val severity: Severity,
    /** Где возникло: "device:<id>", "group:<id>", "shield", и т.п. */
    val scope: String,
    /** Короткий заголовок */
    val title: String,
    /** Детали (1-2 строки) */
    val message: String,
    /** При необходимости — ссылки на нормы */
    val normRefs: List<NormRef> = emptyList()
) {
    enum class Severity { INFO, WARNING, CRITICAL }
}