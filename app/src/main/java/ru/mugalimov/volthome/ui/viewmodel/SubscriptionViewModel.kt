package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.core.analytics.AnalyticsEvent
import ru.mugalimov.volthome.core.analytics.AnalyticsTracker
import ru.mugalimov.volthome.core.analytics.PaywallSource
import ru.mugalimov.volthome.core.analytics.PurchaseAnalyticsContext
import ru.mugalimov.volthome.data.billing.BillingAvailability
import ru.mugalimov.volthome.data.billing.BillingErrorCode
import ru.mugalimov.volthome.data.billing.BillingException
import ru.mugalimov.volthome.data.billing.RustoreBillingManager
import ru.mugalimov.volthome.data.repository.SubscriptionRepository
import ru.rustore.sdk.pay.model.SubscriptionPurchase

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val billingManager: RustoreBillingManager,
    private val analytics: AnalyticsTracker,
    private val purchaseAnalyticsContext: PurchaseAnalyticsContext
) : ViewModel() {

    enum class BillingStage {
        IDLE,
        LOADING_PRODUCT,
        READY_TO_PURCHASE,
        PURCHASING,
        RESTORING,
        ENTITLED,
        FAILED
    }

    data class UiState(
        val isLoading: Boolean = false,
        val isProductLoading: Boolean = false,
        val isProductLoaded: Boolean = false,
        val isProductUnavailable: Boolean = false,
        val isRestoreAvailable: Boolean = BuildConfig.BILLING_RESTORE_ENABLED,
        val isRestoring: Boolean = false,
        val errorMessage: String? = null,
        val infoMessage: String? = null,
        val stage: BillingStage = BillingStage.IDLE,
        val purchaseFlowId: String? = null,
        val productPriceLabel: String? = null,
    )

    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    private val proProduct by lazy { subscriptionRepository.getProProducts().first() }

    fun loadProProductIfNeeded() {
        viewModelScope.launch {
            val current = mutableState.value
            if (current.isProductLoaded || current.isProductLoading) return@launch

            mutableState.value = current.copy(
                isProductLoading = true,
                isProductUnavailable = false,
                errorMessage = null,
                stage = BillingStage.LOADING_PRODUCT
            )

            billingManager.loadProducts(
                productIds = listOf(proProduct.productId),
                flowId = newFlowId("product")
            ).fold(
                onSuccess = { products ->
                    val loadedProduct = products.firstOrNull { it.id == proProduct.productId }
                    val loaded = loadedProduct != null
                    mutableState.value = mutableState.value.copy(
                        isProductLoading = false,
                        isProductLoaded = loaded,
                        isProductUnavailable = !loaded,
                        productPriceLabel = loadedProduct?.priceLabel,
                        stage = if (loaded) BillingStage.READY_TO_PURCHASE else BillingStage.FAILED
                    )
                },
                onFailure = { error ->
                    mutableState.value = mutableState.value.copy(
                        isProductLoading = false,
                        isProductLoaded = false,
                        errorMessage = billingMessage(error),
                        stage = BillingStage.FAILED
                    )
                }
            )
        }
    }

    /** Сверяет локальное право с активными подписками RuStore. */
    fun refreshStatus() {
        viewModelScope.launch {
            val flowId = newFlowId("status")
            mutableState.value = mutableState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            billingManager.getActiveSubscriptions(flowId).fold(
                onSuccess = { subscriptions ->
                    val active = subscriptions.hasVoltHomePro()
                    if (active) subscriptionRepository.activatePro()
                    else subscriptionRepository.deactivatePro()

                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        infoMessage = if (active) "Подписка ВольтХом PRO активна" else null,
                        stage = if (active) BillingStage.ENTITLED else BillingStage.IDLE
                    )
                },
                onFailure = { error ->
                    // При временной ошибке RuStore локальный PRO не сбрасываем.
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        errorMessage = billingMessage(error),
                        stage = BillingStage.FAILED
                    )
                }
            )
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            if (!BuildConfig.BILLING_RESTORE_ENABLED || mutableState.value.isRestoring) return@launch

            val flowId = newFlowId("restore")
            mutableState.value = mutableState.value.copy(
                isLoading = true,
                isRestoring = true,
                errorMessage = null,
                infoMessage = null,
                stage = BillingStage.RESTORING,
                purchaseFlowId = flowId
            )

            billingManager.getActiveSubscriptions(flowId).fold(
                onSuccess = { subscriptions ->
                    val active = subscriptions.hasVoltHomePro()

                    if (active) subscriptionRepository.activatePro()
                    else subscriptionRepository.deactivatePro()

                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        isRestoring = false,
                        infoMessage = if (active) {
                            "Подписка ВольтХом PRO восстановлена"
                        } else {
                            "Активная подписка ВольтХом PRO не найдена"
                        },
                        stage = if (active) BillingStage.ENTITLED else BillingStage.IDLE
                    )
                },
                onFailure = { error ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        isRestoring = false,
                        errorMessage = billingMessage(error),
                        stage = BillingStage.FAILED
                    )
                }
            )
        }
    }

    fun buyPro() {
        viewModelScope.launch {
            val current = mutableState.value
            if (!current.isProductLoaded || current.isLoading) return@launch

            val flowId = newFlowId("purchase")
            mutableState.value = current.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
                stage = BillingStage.PURCHASING,
                purchaseFlowId = flowId
            )

            when (val availability = billingManager.checkAvailability(flowId)) {
                is BillingAvailability.Unavailable -> {
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        errorMessage = availability.message ?: "Платежи недоступны",
                        stage = BillingStage.FAILED
                    )
                    return@launch
                }
                BillingAvailability.Available -> Unit
            }

            billingManager.purchaseSubscription(
                productId = proProduct.productId,
                flowId = flowId
            ).fold(
                onSuccess = { purchase ->
                    subscriptionRepository.activatePro()
                    analytics.track(
                        AnalyticsEvent.PurchaseSuccess(
                            source = purchaseAnalyticsContext.currentSource()
                                ?: PaywallSource.PRO_SCREEN,
                            productId = purchase.productId
                        )
                    )
                    purchaseAnalyticsContext.clear()
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        infoMessage = "Подписка ВольтХом PRO активирована",
                        stage = BillingStage.ENTITLED
                    )
                },
                onFailure = { error ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        errorMessage = billingMessage(error),
                        stage = BillingStage.FAILED
                    )
                }
            )
        }
    }

    private fun List<SubscriptionPurchase>.hasVoltHomePro(): Boolean =
        any { it.productId.value == proProduct.productId }

    private fun billingMessage(error: Throwable): String {
        val billingError = error as? BillingException ?: return error.message ?: "Ошибка RuStore"
        return when (billingError.code) {
            BillingErrorCode.USER_CANCELLED -> "Покупка отменена"
            BillingErrorCode.RUSTORE_NOT_INSTALLED -> "На устройстве не установлен RuStore"
            BillingErrorCode.RUSTORE_OUTDATED -> "Обновите приложение RuStore"
            BillingErrorCode.NETWORK_ERROR -> "Не удалось связаться с RuStore"
            BillingErrorCode.BILLING_NOT_AVAILABLE -> "Платежи недоступны на этом устройстве"
            BillingErrorCode.APPLICATION_BANNED,
            BillingErrorCode.USER_BANNED,
            BillingErrorCode.MONETIZATION_DISABLED_OR_COMPANY_PROBLEM -> "Платежи временно недоступны"
            BillingErrorCode.UNKNOWN -> billingError.message ?: "Ошибка RuStore"
        }
    }

    private fun newFlowId(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().replace("-", "").take(12)}"
}
