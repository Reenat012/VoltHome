package ru.mugalimov.volthome.ui.onboarding.model

/**
 * Причины закрытия active hint.
 *
 * В commit 1 reason нужен, чтобы:
 * - отделить пользовательское завершение от системной отмены
 * - не писать shown flag при системной отмене
 */
enum class OnboardingDismissReason(
    val persistShownFlag: Boolean
) {
    // Пользователь осознанно подтвердил, что понял подсказку.
    USER_CONFIRMED(true),

    // Пользователь отложил подсказку. Она сможет появиться при следующем
    // подходящем входе в контекст.
    USER_DEFERRED(false),

    // Системная отмена: например, hint снят программно.
    SYSTEM_CANCELLED(false)
}
