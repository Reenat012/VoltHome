package ru.mugalimov.volthome.data.billing

import android.app.Activity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над RuStore Pay SDK.
 *
 * Сейчас реализация — заглушка. Сюда нужно будет перенести логику из гайда:
 * - инициализацию клиента,
 * - старт покупки по productId,
 * - обработку deeplink/интента,
 * - извлечение orderId / purchaseToken.
 */
interface RustoreBillingManager {

    sealed class PurchaseResult {
        data class Success(
            val productId: String,
            val orderId: String,
            val purchaseToken: String
        ) : PurchaseResult()

        object Cancelled : PurchaseResult()

        data class Failed(
            val message: String? = null,
            val throwable: Throwable? = null
        ) : PurchaseResult()
    }

    /**
     * Запустить покупку подписки.
     *
     * activity — текущая Activity (MainActivity),
     * productId — идентификатор продукта в RuStore (например, "volthome_pro_monthly").
     */
    suspend fun launchPurchase(
        activity: Activity,
        productId: String
    ): PurchaseResult
}

/**
 * Временная заглушка, чтобы не ронять приложение, пока не подключен реальный Pay SDK.
 *
 * В релизе НЕ использовать — только на время интеграции.
 */
@Singleton
class RustoreBillingManagerStub @Inject constructor() : RustoreBillingManager {

    override suspend fun launchPurchase(
        activity: Activity,
        productId: String
    ): RustoreBillingManager.PurchaseResult {
        // TODO: подключить реальный RuStore Pay SDK согласно
        // https://www.rustore.ru/help/guides/payments/kotlin-java
        return RustoreBillingManager.PurchaseResult.Failed(
            message = "Оплата через RuStore пока не настроена"
        )
    }
}