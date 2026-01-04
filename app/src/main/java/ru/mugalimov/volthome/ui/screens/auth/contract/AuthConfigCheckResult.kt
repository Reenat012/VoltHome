package ru.mugalimov.volthome.ui.screens.auth.contract

/**
 * Коммит 2 — Минимальный AuthConfigChecker
 *
 * Одна функция. Без DI. Без Android-зависимостей.
 * Используется ViewModel как источник правды о валидности OAuth-конфига.
 */

/**
 * Результат проверки OAuth-конфига.
 */
sealed interface AuthConfigCheckResult {
    object Ok : AuthConfigCheckResult
    data class Invalid(val error: ConfigError) : AuthConfigCheckResult
}

/**
 * Минимальная проверка OAuth-конфига.
 *
 * На этом этапе проверяем только clientId:
 * - пустой → ошибка
 * - непустой → ок
 *
 * Расширение (redirectUri и т.п.) — следующими коммитами.
 */
fun checkAuthConfig(
    clientId: String
): AuthConfigCheckResult {
    return if (clientId.isNotBlank()) {
        AuthConfigCheckResult.Ok
    } else {
        AuthConfigCheckResult.Invalid(ConfigError.MissingClientId)
    }
}