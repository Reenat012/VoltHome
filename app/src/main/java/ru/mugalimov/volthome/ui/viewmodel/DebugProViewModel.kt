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
import ru.mugalimov.volthome.data.billing.BillingRecoveryCoordinator
import ru.mugalimov.volthome.data.billing.pending.PendingConfirmCoordinator
import ru.mugalimov.volthome.data.remote.auth.RefreshGate
import ru.mugalimov.volthome.data.repository.SubscriptionRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository

@HiltViewModel
class DebugProViewModel @Inject constructor(
    app: Application,
    private val userPlanRepository: UserPlanRepository,
    private val refreshGate: RefreshGate,
    private val subscriptionRepository: SubscriptionRepository,
    private val pendingCoordinator: PendingConfirmCoordinator,
    private val billingRecoveryCoordinator: BillingRecoveryCoordinator
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

    fun debugInjectPendingPurchase() {
        viewModelScope.launch {
            val flowId = "debug-pending-${java.util.UUID.randomUUID().toString().take(8)}"

            Log.d("DEBUG_PENDING", "INJECT flowId=$flowId")

            pendingCoordinator.onSdkSuccess(
                flowId = flowId,
                productId = "volthome_pro_monthly",
                orderId = "debug-order-pending",
                purchaseToken = "debug-token-pending"
            )
        }
    }

    fun debugRestoreSamePurchaseAsPending() {
        viewModelScope.launch {
            val flowId = "debug-restore-${java.util.UUID.randomUUID().toString().take(8)}"

            val productId = "volthome_pro_monthly"
            val orderId = "debug-order-pending"
            val purchaseToken = "debug-token-pending"

            val owner = "restore:$flowId"

            Log.d(
                "DEBUG_RESTORE",
                "BEGIN flowId=$flowId productId=$productId orderId=$orderId"
            )

            // 🔥 Пытаемся стартануть restore через coordinator
            val started = try {
                // 👉 тебе нужно будет добавить coordinator в VM (ниже скажу куда)
                billingRecoveryCoordinator.beginRestore(
                    flowId = flowId,
                    reason = "debug_restore_same_purchase"
                )
            } catch (t: Throwable) {
                Log.e("DEBUG_RESTORE", "BEGIN_FAILED flowId=$flowId", t)
                return@launch
            }

            if (!started) {
                Log.w(
                    "DEBUG_RESTORE",
                    "RESTORE_NOT_STARTED flowId=$flowId (blocked by coordinator)"
                )
                return@launch
            }

            try {
                // 🔥 КЛЮЧЕВАЯ ПРОВЕРКА — пробуем захватить ту же identity
                val acquired = billingRecoveryCoordinator.tryAcquireIdentity(
                    productId = productId,
                    orderId = orderId,
                    purchaseToken = purchaseToken,
                    owner = owner
                )

                if (!acquired) {
                    Log.w(
                        "DEBUG_RESTORE",
                        "IDENTITY_REJECTED flowId=$flowId (expected behavior)"
                    )
                    return@launch
                }

                // ❌ Если мы сюда попали — это ПРОВАЛ теста
                Log.e(
                    "DEBUG_RESTORE",
                    "IDENTITY_ACQUIRED_UNEXPECTED flowId=$flowId (BUG)"
                )

                // Теоретически confirm (но сюда заходить нельзя)
                val result = subscriptionRepository.confirmRustorePurchase(
                    productId = productId,
                    orderId = orderId,
                    purchaseToken = purchaseToken,
                    flowId = flowId,
                    source = "debug_restore_same_purchase"
                )

                result.fold(
                    onSuccess = {
                        Log.e(
                            "DEBUG_RESTORE",
                            "CONFIRM_SUCCESS_UNEXPECTED flowId=$flowId (BUG)"
                        )
                    },
                    onFailure = {
                        Log.e(
                            "DEBUG_RESTORE",
                            "CONFIRM_FAILED_UNEXPECTED flowId=$flowId (BUG)"
                        )
                    }
                )

            } finally {
                // 🔓 освобождаем identity
                billingRecoveryCoordinator.releaseIdentity(
                    productId = productId,
                    orderId = orderId,
                    purchaseToken = purchaseToken,
                    owner = owner
                )

                billingRecoveryCoordinator.endRestore(
                    flowId = flowId,
                    reason = "debug_restore_same_purchase"
                )

                Log.d("DEBUG_RESTORE", "END flowId=$flowId")
            }
        }
    }

    fun debugTryAcquireSameIdentityWhileReplay() {
        viewModelScope.launch {
            val productId = "volthome_pro_monthly"
            val orderId = "debug-order-pending"
            val purchaseToken = "debug-token-pending"

            val owner = "debug_identity_probe"

            Log.d(
                "DEBUG_IDENTITY",
                "TRY_ACQUIRE owner=$owner productId=$productId orderId=$orderId"
            )

            val acquired = billingRecoveryCoordinator.tryAcquireIdentity(
                productId = productId,
                orderId = orderId,
                purchaseToken = purchaseToken,
                owner = owner
            )

            Log.d(
                "DEBUG_IDENTITY",
                "RESULT owner=$owner acquired=$acquired"
            )
        }
    }
}