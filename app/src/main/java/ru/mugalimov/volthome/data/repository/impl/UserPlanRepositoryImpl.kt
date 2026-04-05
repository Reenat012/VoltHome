package ru.mugalimov.volthome.data.repository.impl

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.UserPlan

@Singleton
class UserPlanRepositoryImpl @Inject constructor() : UserPlanRepository {

    // План, пришедший с сервера. Это единственный источник entitlement в проде.
    private val _serverPlan = MutableStateFlow(UserPlan.FREE)

    // Debug-only runtime override.
    private val _debugForcePro = MutableStateFlow(false)

    // Итоговый план, который читает UI.
    private val _planFlow = MutableStateFlow(UserPlan.FREE)
    override val planFlow: StateFlow<UserPlan> = _planFlow

    override suspend fun setPlan(plan: UserPlan) {
        _serverPlan.value = plan
        recompute()
    }

    /**
     * ✅ Debug-only переключатель PRO.
     * В release BuildConfig.DEBUG=false, поэтому даже если кто-то вызовет — эффекта не будет.
     */
    override fun setDebugForcePro(enabled: Boolean) {
        _debugForcePro.value = enabled
        recompute()
    }

    override fun isDebugForceProEnabled(): Boolean = _debugForcePro.value

    override suspend fun resetToFree(clearDebugOverride: Boolean) {
        // Сбрасываем серверный план в free, чтобы следующий пользователь
        // не увидел entitlement прошлого пользователя.
        _serverPlan.value = UserPlan.FREE

        // Сбрасываем runtime debug override.
        if (clearDebugOverride) {
            _debugForcePro.value = false
        }

        recompute()
    }

    private fun recompute() {
        _planFlow.value =
            if (BuildConfig.DEBUG && _debugForcePro.value) {
                UserPlan(plan = "pro", planUntilEpochSeconds = null)
            } else {
                _serverPlan.value
            }
    }
}