package ru.mugalimov.volthome.data.billing

import android.app.Activity
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.domain.model.ProProduct
import ru.mugalimov.volthome.domain.model.PurchaseKind

@Singleton
class RustoreBillingManagerImpl @Inject constructor(
    // сюда позже добавишь RuStore Pay client / launcher
) : RustoreBillingManager {

    override suspend fun subscribe(
        activity: Activity,
        product: ProProduct
    ): RustoreBillingManager.PurchaseOutcome {
        if (product.kind != PurchaseKind.SUBSCRIPTION) {
            Log.w("ProPay", "billing blocked: wrong kind=${product.kind} for productId=${product.productId}")
            return RustoreBillingManager.PurchaseOutcome.Error(
                RustoreBillingManager.PurchaseError.WrongProductKind(
                    expected = PurchaseKind.SUBSCRIPTION.name,
                    actual = product.kind.name
                )
            )
        }

        if (activity.isFinishing) {
            return RustoreBillingManager.PurchaseOutcome.Error(
                RustoreBillingManager.PurchaseError.InvalidContext("Activity isFinishing=true")
            )
        }
        if (activity.isDestroyed) {
            return RustoreBillingManager.PurchaseOutcome.Error(
                RustoreBillingManager.PurchaseError.InvalidContext("Activity isDestroyed=true")
            )
        }

        return try {
            Log.d("ProPay", "billing start productId=${product.productId} kind=${product.kind}")

            // TODO: реальный запуск RuStore UI для подписки.
            RustoreBillingManager.PurchaseOutcome.Error(
                RustoreBillingManager.PurchaseError.SdkError(
                    message = "RuStore Pay not wired yet",
                    throwable = IllegalStateException("RuStore Pay not wired yet")
                )
            )
        } catch (e: Throwable) {
            mapThrowableToOutcome(e)
        }
    }

    override suspend fun launchPurchase(
        activity: Activity,
        productId: String
    ): RustoreBillingManager.PurchaseOutcome {
        val product = ProProduct(
            productId = productId,
            kind = PurchaseKind.SUBSCRIPTION
        )
        return subscribe(activity, product)
    }

    override suspend fun restorePurchases(activity: Activity): RustoreBillingManager.RestoreOutcome {
        if (activity.isFinishing) {
            return RustoreBillingManager.RestoreOutcome.Error(
                RustoreBillingManager.PurchaseError.InvalidContext("Activity isFinishing=true")
            )
        }
        if (activity.isDestroyed) {
            return RustoreBillingManager.RestoreOutcome.Error(
                RustoreBillingManager.PurchaseError.InvalidContext("Activity isDestroyed=true")
            )
        }

        return try {
            Log.i("ProPay", "restore start")

            // TODO: реальный restore в RuStore SDK (query purchases / restore / whatever API in your integration).
            // Сейчас — безопасный успех-заглушка, чтобы UX пайплайн работал и не падал.
            Log.i("ProPay", "restore success (stub)")
            RustoreBillingManager.RestoreOutcome.Success
        } catch (e: Throwable) {
            val mapped = mapThrowableToError(e)
            Log.e("ProPay", "restore failed: ${e.message}", e)
            RustoreBillingManager.RestoreOutcome.Error(mapped)
        }
    }

    private fun mapThrowableToOutcome(e: Throwable): RustoreBillingManager.PurchaseOutcome {
        val err = mapThrowableToError(e)
        return RustoreBillingManager.PurchaseOutcome.Error(err)
    }

    private fun mapThrowableToError(e: Throwable): RustoreBillingManager.PurchaseError {
        val className = e::class.qualifiedName.orEmpty()

        if (className.contains("RuStorePayInvalidActivePurchase")) {
            Log.e("ProPay", "caught InvalidActivePurchase: ${e.message}", e)
            return RustoreBillingManager.PurchaseError.InvalidActivePurchase(
                message = e.message,
                throwable = e
            )
        }

        if (className.contains("RuStorePaymentException") || className.contains("rustore", ignoreCase = true)) {
            Log.e("ProPay", "caught RuStore sdk error: ${e.message}", e)
            return RustoreBillingManager.PurchaseError.SdkError(
                message = e.message,
                throwable = e
            )
        }

        Log.e("ProPay", "caught unknown billing error: ${e.message}", e)
        return RustoreBillingManager.PurchaseError.Unknown(
            message = e.message,
            throwable = e
        )
    }
}