package ru.mugalimov.volthome.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.billing.BillingAvailability
import ru.mugalimov.volthome.data.billing.BillingErrorCode
import ru.mugalimov.volthome.data.billing.BillingException
import ru.mugalimov.volthome.data.billing.RustoreBillingManager
import ru.mugalimov.volthome.data.repository.SubscriptionRepository

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val billingManager: RustoreBillingManager
) : ViewModel() {

    companion object {
        private const val TAG = "SubscriptionVM"
    }

    data class UiState(
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val infoMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * ID продукта в RuStore Console.
     */
    private val productId: String = "volthome_pro_monthly"

    fun refreshStatus() {
        viewModelScope.launch {
            Log.d(TAG, "refreshStatus() → start")
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            val result = subscriptionRepository.syncStatus()

            _state.value = result.fold(
                onSuccess = {
                    Log.d(TAG, "refreshStatus() → success, plan updated in UserPlanRepository")
                    _state.value.copy(
                        isLoading = false,
                        infoMessage = null,
                        errorMessage = null
                    )
                },
                onFailure = { e ->
                    Log.e(TAG, "refreshStatus() → failure: ${e.message}", e)
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Не удалось обновить статус подписки"
                    )
                }
            )
        }
    }

    /**
     * Покупка VoltHome PRO через RuStore Pay.
     *
     * Никаких Activity сюда больше не таскаем — RuStorePayClient сам
     * стартует нужную шторку оплаты.
     */
    fun buyPro() {
        viewModelScope.launch {
            Log.d(TAG, "buyPro() → start, productId=$productId")

            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            // На всякий случай можно предварительно проверить доступность,
            // чтобы показать более внятное сообщение до старта оплаты.
            Log.d(TAG, "buyPro() → checkAvailability()")
            val availability = try {
                billingManager.checkAvailability()
            } catch (t: Throwable) {
                Log.e(TAG, "buyPro() → checkAvailability() threw exception: ${t.message}", t)
                _state.value = UiState(
                    isLoading = false,
                    errorMessage = t.message ?: "Ошибка проверки доступности платежей"
                )
                return@launch
            }

            when (availability) {
                is BillingAvailability.Unavailable -> {
                    val msg = availability.message
                        ?: "Платежи недоступны. Попробуйте позже."
                    Log.w(TAG, "buyPro() → Billing unavailable: $msg")
                    _state.value = UiState(
                        isLoading = false,
                        errorMessage = msg
                    )
                    return@launch
                }
                BillingAvailability.Available -> {
                    Log.d(TAG, "buyPro() → Billing available, going to purchaseSubscription()")
                    // всё ок, идём дальше
                }
            }

            // 1. Запускаем покупку подписки в RuStore
            val payResult = try {
                billingManager.purchaseSubscription(productId)
            } catch (t: Throwable) {
                Log.e(TAG, "buyPro() → purchaseSubscription() threw exception: ${t.message}", t)
                _state.value = UiState(
                    isLoading = false,
                    errorMessage = t.message ?: "Не удалось запустить оплату"
                )
                return@launch
            }

            Log.d(TAG, "buyPro() → purchaseSubscription() result: $payResult")

            payResult.fold(
                onSuccess = { purchase ->
                    Log.d(
                        TAG,
                        "buyPro() → purchase success: productId=${purchase.productId}, " +
                                "invoiceId=${purchase.invoiceId}, purchaseId=${purchase.purchaseId}"
                    )

                    // 2. Подтверждаем покупку на бэкенде.
                    val confirmResult = subscriptionRepository.confirmRustorePurchase(
                        productId = purchase.productId,
                        orderId = purchase.invoiceId,
                        purchaseToken = purchase.purchaseId,
                    )

                    Log.d(TAG, "buyPro() → confirmRustorePurchase() result: $confirmResult")

                    _state.value = confirmResult.fold(
                        onSuccess = { plan ->
                            Log.d(
                                TAG,
                                "buyPro() → confirm success, new plan=${plan.plan}, until=${plan.planUntilEpochSeconds}"
                            )
                            UiState(
                                isLoading = false,
                                infoMessage = "Подписка VoltHome PRO активирована"
                            )
                        },
                        onFailure = { e ->
                            Log.e(TAG, "buyPro() → confirm failure: ${e.message}", e)
                            UiState(
                                isLoading = false,
                                errorMessage = e.message ?: "Ошибка подтверждения покупки"
                            )
                        }
                    )
                },
                onFailure = { throwable ->
                    val msg = when (throwable) {
                        is BillingException -> mapBillingErrorToMessage(throwable)
                        else -> throwable.message ?: "Не удалось запустить оплату"
                    }

                    Log.w(TAG, "buyPro() → purchase failed: $msg", throwable)

                    _state.value = UiState(
                        isLoading = false,
                        errorMessage = msg
                    )
                }
            )
        }
    }

    private fun mapBillingErrorToMessage(e: BillingException): String =
        when (e.code) {
            BillingErrorCode.USER_CANCELLED ->
                "Покупка отменена"

            BillingErrorCode.RUSTORE_NOT_INSTALLED ->
                "На устройстве не установлен RuStore. Установите магазин и попробуйте снова."

            BillingErrorCode.RUSTORE_OUTDATED ->
                "RuStore устарел. Обновите магазин и попробуйте снова."

            BillingErrorCode.APPLICATION_BANNED,
            BillingErrorCode.USER_BANNED,
            BillingErrorCode.MONETIZATION_DISABLED_OR_COMPANY_PROBLEM ->
                "Платежи временно недоступны. Попробуйте позже."

            BillingErrorCode.NETWORK_ERROR ->
                "Ошибка сети при обращении к RuStore. Проверьте интернет и попробуйте снова."

            BillingErrorCode.BILLING_NOT_AVAILABLE ->
                "Платежи недоступны на этом устройстве."

            BillingErrorCode.UNKNOWN ->
                e.message ?: "Не удалось выполнить покупку"
        }
}