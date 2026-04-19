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
    // Пользователь осознанно закрыл подсказку.
    USER_DISMISSED(true),

    // Пользователь пропустил подсказку.
    USER_SKIPPED(true),

    // Системная отмена: например, hint снят программно.
    SYSTEM_CANCELLED(false)
}