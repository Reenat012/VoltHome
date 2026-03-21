package ru.mugalimov.volthome.data.billing

import android.content.Intent
import ru.rustore.sdk.pay.model.Purchase
import ru.rustore.sdk.pay.model.SubscriptionPurchase

/**
 * Контракт billing-менеджера поверх RuStore SDK.
 *
 * Commit 1:
 * flowId добавлен только для client-side instrumentation.
 * В бизнес-логику он не вмешивается.
 */
interface RustoreBillingManager {

    /**
     * Проверить доступность биллинга на устройстве.
     */
    suspend fun checkAvailability(
        flowId: String? = null
    ): BillingAvailability

    /**
     * Загрузить продукты из RuStore SDK.
     */
    suspend fun loadProducts(
        productIds: List<String>,
        flowId: String? = null
    ): Result<List<BillingProduct>>

    /**
     * Купить подписку.
     */
    suspend fun purchaseSubscription(
        productId: String,
        appUserId: String? = null,
        appUserEmail: String? = null,
        flowId: String? = null
    ): Result<PurchaseCompleted>

    /**
     * Восстановить покупки.
     */
    suspend fun restorePurchases(
        flowId: String? = null
    ): Result<List<Purchase>>

    /**
     * Получить активные подписки.
     */
    suspend fun getActiveSubscriptions(
        flowId: String? = null
    ): Result<List<SubscriptionPurchase>>

    /**
     * Проброс deeplink intent в RuStore SDK.
     */
    fun handleDeeplinkIntent(
        intent: Intent?,
        flowId: String? = null
    )
}