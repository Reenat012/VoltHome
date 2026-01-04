package ru.mugalimov.volthome.data.billing

import android.app.Activity
import ru.mugalimov.volthome.domain.model.ProProduct

interface RustoreBillingManager {

    sealed class PurchaseOutcome {
        data class Success(
            val productId: String,
            val orderId: String,
            val purchaseToken: String
        ) : PurchaseOutcome()

        object Cancelled : PurchaseOutcome()

        data class Error(val error: PurchaseError) : PurchaseOutcome()
    }

    sealed class RestoreOutcome {
        object Success : RestoreOutcome()
        data class Error(val error: PurchaseError) : RestoreOutcome()
    }

    sealed class PurchaseError {
        data class WrongProductKind(
            val expected: String,
            val actual: String
        ) : PurchaseError()

        data class InvalidContext(val reason: String) : PurchaseError()

        data class InvalidActivePurchase(
            val message: String?,
            val throwable: Throwable
        ) : PurchaseError()

        data class SdkError(
            val message: String?,
            val throwable: Throwable
        ) : PurchaseError()

        data class Unknown(
            val message: String?,
            val throwable: Throwable
        ) : PurchaseError()
    }

    suspend fun subscribe(
        activity: Activity,
        product: ProProduct
    ): PurchaseOutcome

    /**
     * Backward-compat: пока VM дергает покупку по productId.
     */
    suspend fun launchPurchase(
        activity: Activity,
        productId: String
    ): PurchaseOutcome

    /**
     * Restore purchases entrypoint (должен НЕ бросать исключения).
     */
    suspend fun restorePurchases(activity: Activity): RestoreOutcome
}