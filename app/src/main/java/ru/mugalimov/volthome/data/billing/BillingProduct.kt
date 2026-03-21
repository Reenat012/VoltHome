package ru.mugalimov.volthome.data.billing

import ru.rustore.sdk.pay.model.ProductType

/**
 * Нормализованная модель продукта для UI/домена.
 */
data class BillingProduct(
    val id: String,
    val type: ProductType,
    val title: String,
    val description: String?,
    val priceLabel: String,
    val currency: String,
    val isSubscription: Boolean
)