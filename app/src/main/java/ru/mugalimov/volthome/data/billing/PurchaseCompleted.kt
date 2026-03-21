package ru.mugalimov.volthome.data.billing

import ru.rustore.sdk.pay.model.ProductType

/**
 * Нормализованный результат успешной покупки из SDK.
 */
data class PurchaseCompleted(
    val productId: String,
    val purchaseId: String,
    val invoiceId: String,
    val purchaseType: String,
    val productType: ProductType,
    val sandbox: Boolean
)