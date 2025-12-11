package ru.mugalimov.volthome.data.billing

import android.content.Intent
import ru.rustore.sdk.pay.model.ProductType
import ru.rustore.sdk.pay.model.Purchase
import ru.rustore.sdk.pay.model.PurchaseType
import ru.rustore.sdk.pay.model.SubscriptionPurchase

/**
 * Высокоуровневый менеджер поверх RuStore Pay SDK.
 *
 * Ничего сам не ходит на твой бэкенд — только общается с RuStore.
 * Валидацию на сервере будешь делать уровнем выше (через SubscriptionRepository/BillingApi).
 */
interface RustoreBillingManager {

    /**
     * Проверка, можно ли вообще работать с платежами.
     * Оборачивает результат getPurchaseAvailability().
     */
    suspend fun checkAvailability(): BillingAvailability

    /**
     * Загрузка продуктов по списку productId из RuStore Console.
     */
    suspend fun loadProducts(productIds: List<String>): Result<List<BillingProduct>>

    /**
     * Покупка подписки (SUBSCRIPTION) в одностадийном режиме.
     *
     * На уровне RuStore подписки **только ONE_STEP**, TWO_STEP для них недоступен.
     */
    suspend fun purchaseSubscription(
        productId: String,
        appUserId: String? = null,
        appUserEmail: String? = null,
    ): Result<PurchaseCompleted>

    /**
     * Восстановление всех покупок пользователя (products + subscriptions).
     * Фильтрация по статусам PAID/CONFIRMED/ACTIVE/PAUSED — встроена в SDK.
     */
    suspend fun restorePurchases(): Result<List<Purchase>>

    /**
     * Только активные подписки (SubscriptionPurchase + статус ACTIVE/PAUSED).
     */
    suspend fun getActiveSubscriptions(): Result<List<SubscriptionPurchase>>

    /**
     * Проброс диплинка / интента из RuStore Pay SDK
     * (onCreate / onNewIntent активити).
     */
    fun handleDeeplinkIntent(intent: Intent?)
}

/**
 * Результат доступности биллинга.
 */
sealed class BillingAvailability {
    object Available : BillingAvailability()

    data class Unavailable(
        val code: BillingErrorCode,
        val message: String?,
        val cause: Throwable? = null,
    ) : BillingAvailability()
}

/**
 * Продукт, адаптированный под твой домен.
 */
data class BillingProduct(
    val id: String,
    val type: ProductType,
    val title: String,
    val description: String?,
    val priceLabel: String,
    val currency: String,
    val isSubscription: Boolean,
)

/**
 * Итог успешной покупки (цифровой товар/подписка).
 * Этого достаточно, чтобы дальше валидировать на бэкенде.
 */
data class PurchaseCompleted(
    val productId: String,
    val purchaseId: String,
    val invoiceId: String,
    val purchaseType: PurchaseType,
    val productType: ProductType,
    val sandbox: Boolean,
)

/**
 * Коды ошибок, которые тебе реально нужны наверху.
 */
enum class BillingErrorCode {
    RUSTORE_NOT_INSTALLED,
    RUSTORE_OUTDATED,
    APPLICATION_BANNED,
    USER_BANNED,
    MONETIZATION_DISABLED_OR_COMPANY_PROBLEM,
    NETWORK_ERROR,
    USER_CANCELLED,
    BILLING_NOT_AVAILABLE,
    UNKNOWN,
}

/**
 * Исключение, которое мы кидаем в Result.onFailure().
 * Внутри всегда есть BillingErrorCode.
 */
class BillingException(
    val code: BillingErrorCode,
    override val message: String?,
    override val cause: Throwable? = null,
) : Exception(message, cause)

