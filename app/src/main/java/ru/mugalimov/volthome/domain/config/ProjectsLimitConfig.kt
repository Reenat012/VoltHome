package ru.mugalimov.volthome.domain.config

/**
 * Единая точка настройки лимита проектов.
 *
 * Важно:
 * - FREE_PROJECTS_LIMIT должен существовать в одном месте.
 * - UNLIMITED — семантика "лимит снят".
 */
object ProjectsLimitConfig {

    /** Free: максимум активных проектов */
    const val FREE_PROJECTS_LIMIT: Int = 3

    /**
     * Семантика "безлимит".
     * Используем Int.MAX_VALUE, чтобы можно было безопасно сравнивать:
     * count >= limit (для безлимита это почти всегда false).
     */
    const val UNLIMITED: Int = Int.MAX_VALUE
}