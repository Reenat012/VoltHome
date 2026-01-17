package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.StateFlow
import ru.mugalimov.volthome.domain.model.UserPlan

/**
 * Хранит текущий тариф пользователя (free/pro) на клиенте.
 * Источник истины — серверный профиль, здесь только производный стейт.
 */
interface UserPlanRepository {

    /** Текущий план пользователя. По умолчанию — FREE. */
    val planFlow: StateFlow<UserPlan>

    /**
     * Обновить план (например, после загрузки профиля).
     */
    suspend fun setPlan(plan: UserPlan)

    /**
     * ✅ Debug-only форс PRO.
     * В release BuildConfig.DEBUG=false, поэтому реализация обязана быть no-op.
     */
    fun setDebugForcePro(enabled: Boolean)

    /**
     * ✅ Debug-only: текущее состояние форса.
     * В release может всегда возвращать false.
     */
    fun isDebugForceProEnabled(): Boolean
}