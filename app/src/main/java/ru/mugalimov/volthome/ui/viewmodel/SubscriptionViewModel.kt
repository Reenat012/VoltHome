package ru.mugalimov.volthome.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
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

    /**
     * Базовая stage model для instrumentation baseline.
     * Здесь это именно модель наблюдаемости, а не полноценная state machine orchestration.
     */
    enum class BillingStage {
        IDLE,
        LOADING_PRODUCT,
        READY_TO_PURCHASE,
        PURCHASING,
        PENDING_CONFIRM,
        CONFIRMING,
        RESTORING,
        ENTITLED,
        FAILED
    }

    data class UiState(
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val infoMessage: String? = null,
        val stage: BillingStage = BillingStage.IDLE,
        val purchaseFlowId: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * ID продукта в RuStore Console.
     */
    private val productId: String = "volthome_pro_monthly"

    fun refreshStatus() {
        viewModelScope.launch {
            // Это не purchaseFlowId покупки, а отдельный id для refresh-операции.
            val flowId = newFlowId(prefix = "status")

            logBegin(
                operation = "refreshStatus",
                flowId = flowId,
                stage = BillingStage.IDLE,
                extra = "action=sync_status"
            )

            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )

            try {
                val result = subscriptionRepository.syncStatus(flowId = flowId)

                _state.value = result.fold(
                    onSuccess = { plan ->
                        logEnd(
                            operation = "refreshStatus",
                            flowId = flowId,
                            stage = BillingStage.IDLE,
                            outcome = "SYNC_STATUS_OK",
                            extra = "plan=${plan.plan}, planUntil=${plan.planUntilEpochSeconds}"
                        )

                        _state.value.copy(
                            isLoading = false,
                            infoMessage = null,
                            errorMessage = null,
                            stage = BillingStage.IDLE
                        )
                    },
                    onFailure = { e ->
                        logEnd(
                            operation = "refreshStatus",
                            flowId = flowId,
                            stage = BillingStage.FAILED,
                            outcome = "SYNC_STATUS_FAILED",
                            extra = "errorClass=${e.javaClass.simpleName}, errorMessage=${e.message}"
                        )

                        _state.value.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Не удалось обновить статус подписки",
                            stage = BillingStage.FAILED
                        )
                    }
                )
            } catch (t: Throwable) {
                logEnd(
                    operation = "refreshStatus",
                    flowId = flowId,
                    stage = BillingStage.FAILED,
                    outcome = "SYNC_STATUS_EXCEPTION",
                    extra = "errorClass=${t.javaClass.simpleName}, errorMessage=${t.message}"
                )

                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = t.message ?: "Не удалось обновить статус подписки",
                    stage = BillingStage.FAILED
                )
            }
        }
    }

    /**
     * Покупка VoltHome PRO через RuStore Pay.
     *
     * Commit 1:
     * - SDK success не даёт PRO напрямую
     * - purchaseFlowId один на весь клиентский путь
     * - confirm идёт только после SDK success
     */
    fun buyPro() {
        viewModelScope.launch {
            val purchaseFlowId = newFlowId(prefix = "purchase")

            logBegin(
                operation = "buyPro",
                flowId = purchaseFlowId,
                stage = BillingStage.PURCHASING,
                extra = "productId=${maskValue(productId)}"
            )

            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
                stage = BillingStage.PURCHASING,
                purchaseFlowId = purchaseFlowId
            )

            // Шаг 1. Проверка доступности.
            logBegin(
                operation = "checkAvailability",
                flowId = purchaseFlowId,
                stage = BillingStage.PURCHASING
            )

            val availability = try {
                billingManager.checkAvailability(flowId = purchaseFlowId)
            } catch (t: Throwable) {
                logEnd(
                    operation = "checkAvailability",
                    flowId = purchaseFlowId,
                    stage = BillingStage.FAILED,
                    outcome = "CHECK_AVAILABILITY_EXCEPTION",
                    extra = "errorClass=${t.javaClass.simpleName}, errorMessage=${t.message}"
                )

                logEnd(
                    operation = "buyPro",
                    flowId = purchaseFlowId,
                    stage = BillingStage.FAILED,
                    outcome = "PURCHASE_FLOW_FAILED",
                    extra = "reason=check_availability_exception"
                )

                _state.value = UiState(
                    isLoading = false,
                    errorMessage = t.message ?: "Ошибка проверки доступности платежей",
                    stage = BillingStage.FAILED,
                    purchaseFlowId = purchaseFlowId
                )
                return@launch
            }

            when (availability) {
                is BillingAvailability.Unavailable -> {
                    val msg = availability.message ?: "Платежи недоступны. Попробуйте позже."

                    logEnd(
                        operation = "checkAvailability",
                        flowId = purchaseFlowId,
                        stage = BillingStage.FAILED,
                        outcome = "BILLING_UNAVAILABLE",
                        extra = "code=${availability.code}, message=${availability.message}"
                    )

                    logEnd(
                        operation = "buyPro",
                        flowId = purchaseFlowId,
                        stage = BillingStage.FAILED,
                        outcome = "PURCHASE_FLOW_FAILED",
                        extra = "reason=billing_unavailable"
                    )

                    _state.value = UiState(
                        isLoading = false,
                        errorMessage = msg,
                        stage = BillingStage.FAILED,
                        purchaseFlowId = purchaseFlowId
                    )
                    return@launch
                }

                BillingAvailability.Available -> {
                    logEnd(
                        operation = "checkAvailability",
                        flowId = purchaseFlowId,
                        stage = BillingStage.PURCHASING,
                        outcome = "BILLING_AVAILABLE"
                    )
                }
            }

            // Шаг 2. SDK purchase.
            logBegin(
                operation = "purchaseSubscription",
                flowId = purchaseFlowId,
                stage = BillingStage.PURCHASING,
                extra = "productId=${maskValue(productId)}"
            )

            val payResult = try {
                billingManager.purchaseSubscription(
                    productId = productId,
                    flowId = purchaseFlowId
                )
            } catch (t: Throwable) {
                logEnd(
                    operation = "purchaseSubscription",
                    flowId = purchaseFlowId,
                    stage = BillingStage.FAILED,
                    outcome = "PURCHASE_EXCEPTION",
                    extra = "errorClass=${t.javaClass.simpleName}, errorMessage=${t.message}"
                )

                logEnd(
                    operation = "buyPro",
                    flowId = purchaseFlowId,
                    stage = BillingStage.FAILED,
                    outcome = "PURCHASE_FLOW_FAILED",
                    extra = "reason=purchase_exception"
                )

                _state.value = UiState(
                    isLoading = false,
                    errorMessage = t.message ?: "Не удалось запустить оплату",
                    stage = BillingStage.FAILED,
                    purchaseFlowId = purchaseFlowId
                )
                return@launch
            }

            payResult.fold(
                onSuccess = { purchase ->
                    logEnd(
                        operation = "purchaseSubscription",
                        flowId = purchaseFlowId,
                        stage = BillingStage.PENDING_CONFIRM,
                        outcome = "PURCHASE_SDK_SUCCESS",
                        extra = buildString {
                            append("productId=").append(maskValue(purchase.productId))
                            append(", orderId=").append(maskValue(purchase.invoiceId))
                            append(", purchaseToken=").append(maskValue(purchase.purchaseId))
                        }
                    )

                    // SDK success — это только переход в ожидание server confirm.
                    _state.value = _state.value.copy(
                        isLoading = true,
                        stage = BillingStage.PENDING_CONFIRM,
                        purchaseFlowId = purchaseFlowId
                    )

                    // Шаг 3. Backend confirm.
                    logBegin(
                        operation = "confirmRustorePurchase",
                        flowId = purchaseFlowId,
                        stage = BillingStage.CONFIRMING,
                        extra = buildString {
                            append("productId=").append(maskValue(purchase.productId))
                            append(", orderId=").append(maskValue(purchase.invoiceId))
                            append(", purchaseToken=").append(maskValue(purchase.purchaseId))
                        }
                    )

                    _state.value = _state.value.copy(
                        isLoading = true,
                        stage = BillingStage.CONFIRMING,
                        purchaseFlowId = purchaseFlowId
                    )

                    val confirmResult = subscriptionRepository.confirmRustorePurchase(
                        productId = purchase.productId,
                        orderId = purchase.invoiceId,
                        purchaseToken = purchase.purchaseId,
                        flowId = purchaseFlowId
                    )

                    _state.value = confirmResult.fold(
                        onSuccess = { plan ->
                            logEnd(
                                operation = "confirmRustorePurchase",
                                flowId = purchaseFlowId,
                                stage = BillingStage.ENTITLED,
                                outcome = "CONFIRM_OK",
                                extra = "plan=${plan.plan}, planUntil=${plan.planUntilEpochSeconds}"
                            )

                            logEnd(
                                operation = "buyPro",
                                flowId = purchaseFlowId,
                                stage = BillingStage.ENTITLED,
                                outcome = "PURCHASE_FLOW_OK",
                                extra = "finalPlan=${plan.plan}"
                            )

                            UiState(
                                isLoading = false,
                                infoMessage = "Подписка ВольтХом PRO активирована",
                                stage = BillingStage.ENTITLED,
                                purchaseFlowId = purchaseFlowId
                            )
                        },
                        onFailure = { e ->
                            logEnd(
                                operation = "confirmRustorePurchase",
                                flowId = purchaseFlowId,
                                stage = BillingStage.FAILED,
                                outcome = "CONFIRM_FAILED",
                                extra = "errorClass=${e.javaClass.simpleName}, errorMessage=${e.message}"
                            )

                            logEnd(
                                operation = "buyPro",
                                flowId = purchaseFlowId,
                                stage = BillingStage.FAILED,
                                outcome = "PURCHASE_FLOW_FAILED_AFTER_SDK_SUCCESS",
                                extra = "errorClass=${e.javaClass.simpleName}, errorMessage=${e.message}"
                            )

                            UiState(
                                isLoading = false,
                                errorMessage = e.message ?: "Ошибка подтверждения покупки",
                                stage = BillingStage.FAILED,
                                purchaseFlowId = purchaseFlowId
                            )
                        }
                    )
                },
                onFailure = { throwable ->
                    val msg = when (throwable) {
                        is BillingException -> mapBillingErrorToMessage(throwable)
                        else -> throwable.message ?: "Не удалось запустить оплату"
                    }

                    val outcome = when ((throwable as? BillingException)?.code) {
                        BillingErrorCode.USER_CANCELLED -> "PURCHASE_CANCELLED"
                        else -> "PURCHASE_FAILED"
                    }

                    logEnd(
                        operation = "purchaseSubscription",
                        flowId = purchaseFlowId,
                        stage = BillingStage.FAILED,
                        outcome = outcome,
                        extra = "errorClass=${throwable.javaClass.simpleName}, errorMessage=${throwable.message}"
                    )

                    logEnd(
                        operation = "buyPro",
                        flowId = purchaseFlowId,
                        stage = BillingStage.FAILED,
                        outcome = outcome,
                        extra = "errorClass=${throwable.javaClass.simpleName}, errorMessage=${throwable.message}"
                    )

                    _state.value = UiState(
                        isLoading = false,
                        errorMessage = msg,
                        stage = BillingStage.FAILED,
                        purchaseFlowId = purchaseFlowId
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

    /**
     * Генерация короткого flow id для трассировки.
     */
    private fun newFlowId(prefix: String): String {
        val tail = UUID.randomUUID().toString().replace("-", "").take(12)
        return "$prefix-$tail"
    }

    /**
     * Маскирование чувствительных значений для логов.
     */
    private fun maskValue(value: String?): String {
        if (value.isNullOrBlank()) return "null"
        return when {
            value.length <= 4 -> "***$value"
            value.length <= 8 -> "${value.take(1)}***${value.takeLast(2)}"
            else -> "${value.take(3)}***${value.takeLast(4)}"
        }
    }

    /**
     * Лог начала шага.
     */
    private fun logBegin(
        operation: String,
        flowId: String,
        stage: BillingStage,
        extra: String? = null
    ) {
        val message = buildString {
            append("BEGIN")
            append(" op=").append(operation)
            append(" flowId=").append(flowId)
            append(" stage=").append(stage.name)
            if (!extra.isNullOrBlank()) {
                append(" ").append(extra)
            }
        }
        Log.d(TAG, message)
    }

    /**
     * Лог завершения шага.
     */
    private fun logEnd(
        operation: String,
        flowId: String,
        stage: BillingStage,
        outcome: String,
        extra: String? = null
    ) {
        val message = buildString {
            append("END")
            append(" op=").append(operation)
            append(" flowId=").append(flowId)
            append(" stage=").append(stage.name)
            append(" outcome=").append(outcome)
            if (!extra.isNullOrBlank()) {
                append(" ").append(extra)
            }
        }
        Log.d(TAG, message)
    }
}