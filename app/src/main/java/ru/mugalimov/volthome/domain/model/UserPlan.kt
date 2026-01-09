package ru.mugalimov.volthome.domain.model

/**
 * Модель тарифа пользователя.
 *
 * plan: "free" | "pro" (можно расширять дальше, если появятся другие тарифы)
 * planUntilEpochSeconds: Unix-время окончания действия PRO (в секундах) или null.
 */
data class UserPlan(
    val plan: String,
    val planUntilEpochSeconds: Long?
) {

    /**
     * Пользователь считается PRO, если:
     * - план = "pro"
     * - и срок действия не истёк (или null, если бессрочно).
     */
    val isPro: Boolean
        get() = plan == "pro" &&
                (planUntilEpochSeconds == null ||
                        planUntilEpochSeconds * 1000L > System.currentTimeMillis())

    val capabilities: PlanCapabilities
        get() = PlanCapabilities.fromPlan(this)

    companion object {
        val FREE = UserPlan(plan = "free", planUntilEpochSeconds = null)
    }
}