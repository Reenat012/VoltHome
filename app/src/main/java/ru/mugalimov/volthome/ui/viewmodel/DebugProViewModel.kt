package ru.mugalimov.volthome.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.remote.auth.RefreshGate
import ru.mugalimov.volthome.data.repository.SubscriptionRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository

@HiltViewModel
class DebugProViewModel @Inject constructor(
    app: Application,
    private val userPlanRepository: UserPlanRepository,
    private val refreshGate: RefreshGate,
    private val subscriptionRepository: SubscriptionRepository
) : AndroidViewModel(app) {

    private val prefs =
        app.getSharedPreferences("debug_pro", Application.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    init {
        val saved = prefs.getBoolean("force_pro", false)
        _enabled.value = saved
        apply(saved)
    }

    fun setEnabled(v: Boolean) {
        _enabled.value = v
        prefs.edit().putBoolean("force_pro", v).apply()
        apply(v)
    }

    private fun apply(v: Boolean) {
        userPlanRepository.setDebugForcePro(v)
    }

    fun forceRefreshSession() {
        viewModelScope.launch {
            val result = refreshGate.forceRefresh()
            Log.d("DEBUG_REFRESH", "result=$result")
        }
    }

    fun debugCallConfirmDirectly() {
        viewModelScope.launch {
            val flowId = "debug-confirm-${java.util.UUID.randomUUID().toString().take(8)}"

            Log.d(
                "DEBUG_CONFIRM",
                "BEGIN flowId=$flowId direct=true productId=volthome_pro_monthly"
            )

            val result = subscriptionRepository.confirmRustorePurchase(
                productId = "volthome_pro_monthly",
                orderId = "debug-order-trm-b",
                purchaseToken = "debug-token-trm",
                flowId = flowId
            )

            result.fold(
                onSuccess = { plan ->
                    Log.d(
                        "DEBUG_CONFIRM",
                        "END flowId=$flowId outcome=SUCCESS plan=${plan.plan} planUntil=${plan.planUntilEpochSeconds}"
                    )
                },
                onFailure = { t ->
                    Log.e(
                        "DEBUG_CONFIRM",
                        "END flowId=$flowId outcome=FAILED errorClass=${t.javaClass.simpleName} errorMessage=${t.message}",
                        t
                    )
                }
            )
        }
    }
}