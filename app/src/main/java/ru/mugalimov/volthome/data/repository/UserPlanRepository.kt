package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.StateFlow
import ru.mugalimov.volthome.domain.model.UserPlan

/**
 * Постоянно хранит текущий тариф (free/pro) на устройстве.
 * RuStore используется для покупки и восстановления, собственного backend нет.
 */
interface UserPlanRepository {

    /** Текущий план пользователя. По умолчанию — FREE. */
    val planFlow: StateFlow<UserPlan>

    /**
     * Обновить и сохранить локальный план.
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

    /**
     * Сбросить локальное entitlement-состояние. Выход из профиля этот метод не вызывает,
     * потому что покупка относится к устройству/RuStore, а не к Яндекс ID.
     */
    suspend fun resetToFree(clearDebugOverride: Boolean = true)
}
