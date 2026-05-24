package ru.mugalimov.volthome.domain.model

/**
 * Тип продукта в RuStore Pay.
 *
 * Важно: тип должен совпадать с тем, как SKU заведён в RuStore Console.
 */
enum class PurchaseKind {
    SUBSCRIPTION,
    INAPP
}

/**
 * Описание PRO-продукта, который мы показываем пользователю и передаём в billing.
 */
data class ProProduct(
    val productId: String,
    val kind: PurchaseKind
)

// Единственная точка правды для PRO SKU (пока один продукт).
const val VOLTHOME_PRO_MONTHLY_PRODUCT_ID: String = "volthome.pro.monthly"