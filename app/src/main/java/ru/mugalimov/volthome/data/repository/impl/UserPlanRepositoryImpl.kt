package ru.mugalimov.volthome.data.repository.impl

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.data.local.datastore.AppPreferences
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.UserPlan

@Singleton
class UserPlanRepositoryImpl @Inject constructor(
    private val preferences: AppPreferences
) : UserPlanRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storedPlan = MutableStateFlow(UserPlan.FREE)
    private val debugForcePro = MutableStateFlow(false)
    private val mutablePlanFlow = MutableStateFlow(UserPlan.FREE)

    override val planFlow: StateFlow<UserPlan> = mutablePlanFlow

    init {
        scope.launch {
            preferences.userPlan.collect { plan ->
                storedPlan.value = if (plan.isPro) plan else UserPlan.FREE
                recompute()
            }
        }
    }

    override suspend fun setPlan(plan: UserPlan) {
        val normalized = if (plan.isPro) plan else UserPlan.FREE
        storedPlan.value = normalized
        preferences.setUserPlan(normalized)
        recompute()
    }

    override fun setDebugForcePro(enabled: Boolean) {
        debugForcePro.value = enabled
        recompute()
    }

    override fun isDebugForceProEnabled(): Boolean = debugForcePro.value

    override suspend fun resetToFree(clearDebugOverride: Boolean) {
        storedPlan.value = UserPlan.FREE
        preferences.setUserPlan(UserPlan.FREE)
        if (clearDebugOverride) debugForcePro.value = false
        recompute()
    }

    private fun recompute() {
        mutablePlanFlow.value = if (BuildConfig.DEBUG && debugForcePro.value) {
            UserPlan(plan = "pro", planUntilEpochSeconds = null)
        } else {
            storedPlan.value
        }
    }
}
