package ru.mugalimov.volthome.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.billing.RustoreBillingManager
import ru.mugalimov.volthome.data.repository.SubscriptionRepository

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val billingManager: RustoreBillingManager
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val infoMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * ID продукта в RuStore Console.
     * TODO: замени на фактический productId, например "volthome_pro_monthly".
     */
    private val productId: String = "volthome_pro_monthly"

    fun refreshStatus() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            val result = subscriptionRepository.syncStatus()
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        isLoading = false,
                        infoMessage = null,
                        errorMessage = null
                    )
                },
                onFailure = { e ->
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Не удалось обновить статус подписки"
                    )
                }
            )
        }
    }

    fun buyPro(activity: Activity) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            // 1. Запускаем покупку через RuStore Pay (сейчас заглушка)
            when (val payResult = billingManager.launchPurchase(activity, productId)) {
                is RustoreBillingManager.PurchaseResult.Cancelled -> {
                    _state.value = UiState(
                        isLoading = false,
                        infoMessage = "Покупка отменена"
                    )
                }

                is RustoreBillingManager.PurchaseResult.Failed -> {
                    _state.value = UiState(
                        isLoading = false,
                        errorMessage = payResult.message ?: "Ошибка запуска оплаты"
                    )
                }

                is RustoreBillingManager.PurchaseResult.Success -> {
                    // 2. Подтверждаем покупку на бэкенде
                    val result = subscriptionRepository.confirmRustorePurchase(
                        productId = payResult.productId,
                        orderId = payResult.orderId,
                        purchaseToken = payResult.purchaseToken
                    )

                    _state.value = result.fold(
                        onSuccess = {
                            UiState(
                                isLoading = false,
                                infoMessage = "Подписка VoltHome PRO активирована"
                            )
                        },
                        onFailure = { e ->
                            UiState(
                                isLoading = false,
                                errorMessage = e.message ?: "Ошибка подтверждения покупки"
                            )
                        }
                    )
                }
            }
        }
    }
}