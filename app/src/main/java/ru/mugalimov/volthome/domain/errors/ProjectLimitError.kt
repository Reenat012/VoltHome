package ru.mugalimov.volthome.domain.errors

/**
 * Ошибки, связанные с ограничениями по количеству проектов.
 *
 * Это доменный слой: никаких UI/строк/ресурсов.
 */
sealed interface ProjectLimitError {

    /**
     * Достигнут лимит проектов для текущего плана.
     *
     * limit: лимит (например 3)
     * current: текущее количество активных проектов
     * isPro: полезно для логики аналитики/диагностики (не для UI-текста)
     */
    data class ProjectLimitReached(
        val limit: Int,
        val current: Int,
        val isPro: Boolean,
    ) : ProjectLimitError
}