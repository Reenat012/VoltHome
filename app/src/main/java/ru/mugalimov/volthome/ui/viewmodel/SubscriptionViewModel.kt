package ru.mugalimov.volthome.ui.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.billing.RustoreBillingManager
import ru.mugalimov.volthome.data.repository.SubscriptionRepository
import ru.mugalimov.volthome.domain.model.PurchaseKind
import ru.mugalimov.volthome.domain.model.VOLTHOME_PRO_MONTHLY_PRODUCT_ID

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

    sealed class UiEvent {
        object ShowInvalidActivePurchaseDialog : UiEvent()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    // Guard G3: single-flight lock
    private var isPurchaseInFlight: Boolean = false
    private var isRestoreInFlight: Boolean = false

    fun refreshStatus() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            val result = subscriptionRepository.syncStatus()
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(isLoading = false, infoMessage = null, errorMessage = null)
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

    fun restorePurchases(activity: Activity) {
        viewModelScope.launch {
            if (isRestoreInFlight) {
                Log.w("ProPay", "restore blocked - in flight")
                return@launch
            }

            isRestoreInFlight = true
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            try {
                when (val restore = billingManager.restorePurchases(activity)) {
                    is RustoreBillingManager.RestoreOutcome.Success -> {
                        Log.i("ProPay", "restore success")
                        Log.i("ProPay", "refreshPlan after restore")
                        // после restore — обновляем план с сервера
                        val planRes = subscriptionRepository.syncStatus()
                        _state.value = planRes.fold(
                            onSuccess = { plan ->
                                if (plan.isPro) {
                                    UiState(isLoading = false, infoMessage = "Покупки восстановлены. PRO активен.")
                                } else {
                                    UiState(isLoading = false, infoMessage = "Покупки восстановлены. Активной подписки не найдено.")
                                }
                            },
                            onFailure = { e ->
                                UiState(isLoading = false, errorMessage = e.message ?: "Не удалось обновить статус после восстановления")
                            }
                        )
                    }

                    is RustoreBillingManager.RestoreOutcome.Error -> {
                        Log.w("ProPay", "restore failed: ${restore.error}")
                        _state.value = UiState(
                            isLoading = false,
                            errorMessage = when (restore.error) {
                                is RustoreBillingManager.PurchaseError.InvalidContext -> "Нельзя восстановить покупки: экран уже закрывается."
                                is RustoreBillingManager.PurchaseError.InvalidActivePurchase -> "Не удалось восстановить покупки из-за некорректного активного состояния."
                                is RustoreBillingManager.PurchaseError.SdkError -> restore.error.message ?: "Ошибка RuStore при восстановлении"
                                is RustoreBillingManager.PurchaseError.Unknown -> restore.error.message ?: "Неизвестная ошибка восстановления"
                                is RustoreBillingManager.PurchaseError.WrongProductKind -> "Неверный тип продукта (ошибка конфигурации)"
                            }
                        )
                    }
                }
            } finally {
                isRestoreInFlight = false
            }
        }
    }

    fun buyPro(activity: Activity) {
        viewModelScope.launch {
            if (isPurchaseInFlight) {
                Log.w("ProPay", "purchase blocked - in flight")
                _state.value = _state.value.copy(isLoading = false, infoMessage = "Оплата уже открыта")
                return@launch
            }

            // Guard G1: if already PRO — do not start purchase
            subscriptionRepository.syncStatus()
                .onSuccess { plan ->
                    if (plan.isPro) {
                        Log.i("ProPay", "skip purchase - already PRO")
                        _state.value = UiState(isLoading = false, infoMessage = "Подписка уже активна")
                        return@launch
                    }
                }
                .onFailure { e ->
                    Log.w("ProPay", "plan check failed before purchase: ${e.message}", e)
                    // намеренно не блокируем покупку
                }

            _state.value = _state.value.copy(isLoading = true, errorMessage = null, infoMessage = null)

            val products = subscriptionRepository.getProProducts()
            val proProduct = products.firstOrNull {
                it.kind == PurchaseKind.SUBSCRIPTION && it.productId == VOLTHOME_PRO_MONTHLY_PRODUCT_ID
            }

            if (proProduct == null) {
                _state.value = UiState(isLoading = false, errorMessage = "Продукт PRO недоступен (SKU не найден)")
                Log.w("ProPay", "PRO product not found. products=$products")
                return@launch
            }

            Log.d("ProPay", "selected productId=${proProduct.productId} kind=${proProduct.kind}")

            isPurchaseInFlight = true
            try {
                when (val pay = billingManager.subscribe(activity, proProduct)) {
                    is RustoreBillingManager.PurchaseOutcome.Success -> {
                        val result = subscriptionRepository.confirmRustorePurchase(
                            productId = pay.productId,
                            orderId = pay.orderId,
                            purchaseToken = pay.purchaseToken
                        )

                        _state.value = result.fold(
                            onSuccess = { UiState(isLoading = false, infoMessage = "Подписка VoltHome PRO активирована") },
                            onFailure = { e -> UiState(isLoading = false, errorMessage = e.message ?: "Ошибка подтверждения покупки") }
                        )
                    }

                    is RustoreBillingManager.PurchaseOutcome.Cancelled -> {
                        _state.value = UiState(isLoading = false, infoMessage = "Покупка отменена")
                    }

                    is RustoreBillingManager.PurchaseOutcome.Error -> {
                        when (val err = pay.error) {
                            is RustoreBillingManager.PurchaseError.InvalidActivePurchase -> {
                                Log.w("ProPay", "show invalid-active-purchase dialog")
                                _state.value = UiState(isLoading = false, errorMessage = null, infoMessage = null)
                                _events.tryEmit(UiEvent.ShowInvalidActivePurchaseDialog)
                            }

                            is RustoreBillingManager.PurchaseError.InvalidContext -> {
                                _state.value = UiState(isLoading = false, errorMessage = "Оплата недоступна: экран закрывается.")
                            }

                            is RustoreBillingManager.PurchaseError.WrongProductKind -> {
                                _state.value = UiState(isLoading = false, errorMessage = "Неверный тип продукта (ошибка конфигурации).")
                            }

                            is RustoreBillingManager.PurchaseError.SdkError -> {
                                _state.value = UiState(isLoading = false, errorMessage = err.message ?: "Ошибка RuStore при оплате")
                            }

                            is RustoreBillingManager.PurchaseError.Unknown -> {
                                _state.value = UiState(isLoading = false, errorMessage = err.message ?: "Неизвестная ошибка оплаты")
                            }
                        }
                    }
                }
            } finally {
                isPurchaseInFlight = false
            }
        }
    }
}