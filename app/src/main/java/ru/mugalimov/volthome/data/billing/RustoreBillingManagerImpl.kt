package ru.mugalimov.volthome.data.billing

import android.content.Intent
import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.core.analytics.AnalyticsEvent
import ru.mugalimov.volthome.core.analytics.AnalyticsTracker
import ru.mugalimov.volthome.core.analytics.PaywallSource
import ru.mugalimov.volthome.core.analytics.PurchaseAnalyticsContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.rustore.sdk.core.exception.RuStoreApplicationBannedException
import ru.rustore.sdk.core.exception.RuStoreNotInstalledException
import ru.rustore.sdk.core.exception.RuStoreOutdatedException
import ru.rustore.sdk.core.exception.RuStoreUserBannedException
import ru.rustore.sdk.core.tasks.Task
import ru.rustore.sdk.pay.IntentInteractor
import ru.rustore.sdk.pay.ProductInteractor
import ru.rustore.sdk.pay.PurchaseInteractor
import ru.rustore.sdk.pay.RuStorePayClient
import ru.rustore.sdk.pay.UserInteractor
import ru.rustore.sdk.pay.model.PreferredPurchaseType
import ru.rustore.sdk.pay.model.Product
import ru.rustore.sdk.pay.model.ProductId
import ru.rustore.sdk.pay.model.ProductPurchaseParams
import ru.rustore.sdk.pay.model.ProductPurchaseResult
import ru.rustore.sdk.pay.model.ProductType
import ru.rustore.sdk.pay.model.Purchase
import ru.rustore.sdk.pay.model.PurchaseAvailabilityResult
import ru.rustore.sdk.pay.model.RuStorePaymentException
import ru.rustore.sdk.pay.model.SubscriptionPurchase
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Реализация поверх RuStorePayClient.
 *
 * Commit 2:
 * - не генерируем локальные flow id внутри менеджера;
 * - используем только flowId, который пришёл сверху;
 * - для loadProducts явно различаем empty input / empty result / failure;
 * - пустой список продуктов не считаем ошибкой SDK: это normal success-case,
 *   а решение «product unavailable» принимает уже ViewModel.
 */
class RustoreBillingManagerImpl @Inject constructor(
    private val analytics: AnalyticsTracker,
    private val purchaseAnalyticsContext: PurchaseAnalyticsContext
) : RustoreBillingManager {

    companion object {
        private const val TAG = "RustoreBillingManager"
    }

    // Берём IO-диспетчер без DI, чтобы не плодить лишние биндинги.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val payClient: RuStorePayClient
        get() = RuStorePayClient.instance

    private val productInteractor: ProductInteractor
        get() = payClient.getProductInteractor()

    private val purchaseInteractor: PurchaseInteractor
        get() = payClient.getPurchaseInteractor()

    // Пока не используем явно, но оставляем доступ к interactor на будущее.
    private val userInteractor: UserInteractor
        get() = payClient.getUserInteractor()

    private val intentInteractor: IntentInteractor
        get() = payClient.getIntentInteractor()

    override suspend fun checkAvailability(
        flowId: String?
    ): BillingAvailability = withContext(ioDispatcher) {
        logBegin(
            operation = "billing.checkAvailability",
            flowId = flowId
        )

        try {
            val result = purchaseInteractor.getPurchaseAvailability().await(
                operation = "billing.checkAvailability.await",
                flowId = flowId
            )

            when (result) {
                is PurchaseAvailabilityResult.Available -> {
                    logEnd(
                        operation = "billing.checkAvailability",
                        flowId = flowId,
                        outcome = "AVAILABLE"
                    )
                    BillingAvailability.Available
                }

                is PurchaseAvailabilityResult.Unavailable -> {
                    val ex = mapToBillingException(
                        throwable = result.cause,
                        defaultCode = BillingErrorCode.BILLING_NOT_AVAILABLE,
                        customMessage = "Платежи недоступны: ${result.cause?.message}"
                    )

                    logWarn(
                        operation = "billing.checkAvailability",
                        flowId = flowId,
                        outcome = "UNAVAILABLE",
                        extra = buildString {
                            append("causeClass=").append(result.cause?.javaClass?.simpleName)
                            append(", code=").append(ex.code)
                            append(", message=").append(ex.message)
                        }
                    )

                    BillingAvailability.Unavailable(
                        code = ex.code,
                        message = ex.message,
                        cause = ex.cause
                    )
                }
            }
        } catch (t: Throwable) {
            val ex = mapToBillingException(
                throwable = t,
                defaultCode = BillingErrorCode.BILLING_NOT_AVAILABLE,
                customMessage = "Не удалось проверить доступность платежей: ${t.message}"
            )

            logError(
                operation = "billing.checkAvailability",
                flowId = flowId,
                outcome = "EXCEPTION",
                throwable = t,
                extra = "mappedCode=${ex.code}, mappedMessage=${ex.message}"
            )

            BillingAvailability.Unavailable(
                code = ex.code,
                message = ex.message,
                cause = ex.cause
            )
        }
    }

    override suspend fun loadProducts(
        productIds: List<String>,
        flowId: String?
    ): Result<List<BillingProduct>> = withContext(ioDispatcher) {
        logBegin(
            operation = "billing.loadProducts",
            flowId = flowId,
            extra = "productIds=${productIds.map(::maskValue)}"
        )

        // Пустой вход — не ошибка SDK и не crash-case.
        if (productIds.isEmpty()) {
            logEnd(
                operation = "billing.loadProducts",
                flowId = flowId,
                outcome = "EMPTY_INPUT"
            )
            return@withContext Result.success(emptyList())
        }

        runCatching {
            val products: List<Product> = productInteractor
                .getProducts(
                    productsId = productIds.map { ProductId(it) }
                )
                .await(
                    operation = "billing.loadProducts.await",
                    flowId = flowId
                )

            // Явно мапим SDK-модели в наши UI/домен модели.
            products.map { it.toBillingProduct() }
        }.mapError(
            operation = "billing.loadProducts.mapError",
            flowId = flowId
        ).also { result ->
            result.onSuccess { products ->
                // Пустой результат — это не failure, а корректный сигнал
                // для дальнейнего состояния Product unavailable.
                if (products.isEmpty()) {
                    logWarn(
                        operation = "billing.loadProducts",
                        flowId = flowId,
                        outcome = "EMPTY_RESULT",
                        extra = "requestedCount=${productIds.size}"
                    )
                } else {
                    logEnd(
                        operation = "billing.loadProducts",
                        flowId = flowId,
                        outcome = "OK",
                        extra = buildString {
                            append("requestedCount=").append(productIds.size)
                            append(", loadedCount=").append(products.size)
                            append(", loadedIds=").append(products.map { maskValue(it.id) })
                        }
                    )
                }
            }.onFailure {
                logError(
                    operation = "billing.loadProducts",
                    flowId = flowId,
                    outcome = "FAILED",
                    throwable = it
                )
            }
        }
    }

    override suspend fun purchaseSubscription(
        productId: String,
        appUserId: String?,
        appUserEmail: String?,
        flowId: String?
    ): Result<PurchaseCompleted> = withContext(ioDispatcher) {
        logBegin(
            operation = "billing.purchaseSubscription",
            flowId = flowId,
            extra = buildString {
                append("productId=").append(maskValue(productId))
                append(", appUserId=").append(maskValue(appUserId))
                append(", appUserEmail=").append(maskEmail(appUserEmail))
            }
        )

        runCatching {
            // Внутри purchase делаем ту же pre-check, но не создаём новый flow id.
            when (val availability = checkAvailability(flowId = flowId)) {
                is BillingAvailability.Unavailable -> {
                    throw BillingException(
                        code = availability.code,
                        message = availability.message ?: "Платежи недоступны",
                        cause = availability.cause
                    )
                }

                BillingAvailability.Available -> {
                    // Ничего не делаем, продолжаем.
                }
            }

            val params = ProductPurchaseParams(
                productId = ProductId(productId),
                // quantity применим только к CONSUMABLE, подписка — 1 шт.
                quantity = null,
                orderId = null,
                developerPayload = null,
                // Эти параметры опциональны, null OK — дока это подтверждает.
                appUserId = appUserId?.let { ru.rustore.sdk.pay.model.AppUserId(it) },
                appUserEmail = appUserEmail?.let { ru.rustore.sdk.pay.model.AppUserEmail(it) },
            )

            analytics.track(
                AnalyticsEvent.PurchaseStarted(
                    source = purchaseAnalyticsContext.currentSource()
                        ?: PaywallSource.PRO_SCREEN,
                    productId = productId
                )
            )

            val result: ProductPurchaseResult = purchaseInteractor
                .purchase(
                    params = params,
                    preferredPurchaseType = PreferredPurchaseType.ONE_STEP
                )
                .await(
                    operation = "billing.purchaseSubscription.await",
                    flowId = flowId
                )

            PurchaseCompleted(
                productId = result.productId.value,
                purchaseId = result.purchaseId.value,
                invoiceId = result.invoiceId.value,
                // Приводим тип покупки из SDK к строке,
                // потому что в нашей модели PurchaseCompleted поле purchaseType имеет тип String.
                purchaseType = result.purchaseType.toString(),
                productType = result.productType,
                sandbox = result.sandbox
            )
        }.mapError(
            operation = "billing.purchaseSubscription.mapError",
            flowId = flowId
        ) { throwable ->
            // Отдельно подсвечиваем отмену пользователем.
            if (
                throwable is RuStorePaymentException &&
                throwable.message?.contains("cancel", ignoreCase = true) == true
            ) {
                BillingException(
                    code = BillingErrorCode.USER_CANCELLED,
                    message = "Покупка отменена пользователем",
                    cause = throwable
                )
            } else {
                null
            }
        }.also { result ->
            result.onSuccess {
                logEnd(
                    operation = "billing.purchaseSubscription",
                    flowId = flowId,
                    outcome = "OK",
                    extra = buildString {
                        append("productId=").append(maskValue(it.productId))
                        append(", orderId=").append(maskValue(it.invoiceId))
                        append(", purchaseToken=").append(maskValue(it.purchaseId))
                    }
                )
            }.onFailure {
                val outcome = when ((it as? BillingException)?.code) {
                    BillingErrorCode.USER_CANCELLED -> "CANCELLED"
                    else -> "FAILED"
                }

                logError(
                    operation = "billing.purchaseSubscription",
                    flowId = flowId,
                    outcome = outcome,
                    throwable = it
                )
            }
        }
    }

    override suspend fun restorePurchases(
        flowId: String?
    ): Result<List<Purchase>> = withContext(ioDispatcher) {
        logBegin(
            operation = "billing.restorePurchases",
            flowId = flowId
        )

        runCatching {
            purchaseInteractor
                .getPurchases()
                .await(
                    operation = "billing.restorePurchases.await",
                    flowId = flowId
                )
        }.mapError(
            operation = "billing.restorePurchases.mapError",
            flowId = flowId
        ).also { result ->
            result.onSuccess { purchases ->
                // Отдельно считаем подписки, потому что именно они важны для restore PRO.
                val subscriptions = purchases.filterIsInstance<SubscriptionPurchase>()

                // Для логов даём короткий preview по первым найденным подпискам.
                val preview = subscriptions.take(3).joinToString(separator = "; ") { purchase ->
                    buildString {
                        append("productId=").append(maskValue(purchase.productId.value))
                        append(", orderId=").append(maskValue(purchase.invoiceId.value))
                        append(", purchaseToken=").append(maskValue(purchase.purchaseId.value))
                    }
                }

                val outcome = when {
                    purchases.isEmpty() -> "EMPTY"
                    subscriptions.isEmpty() -> "NO_SUBSCRIPTIONS"
                    else -> "OK"
                }

                logEnd(
                    operation = "billing.restorePurchases",
                    flowId = flowId,
                    outcome = outcome,
                    extra = buildString {
                        append("purchaseCount=").append(purchases.size)
                        append(", subscriptionCount=").append(subscriptions.size)
                        if (preview.isNotBlank()) {
                            append(", preview=[").append(preview).append("]")
                        }
                    }
                )
            }.onFailure {
                logError(
                    operation = "billing.restorePurchases",
                    flowId = flowId,
                    outcome = "FAILED",
                    throwable = it
                )
            }
        }
    }

    override suspend fun getActiveSubscriptions(
        flowId: String?
    ): Result<List<SubscriptionPurchase>> = withContext(ioDispatcher) {
        logBegin(
            operation = "billing.getActiveSubscriptions",
            flowId = flowId
        )

        runCatching {
            val purchases: List<Purchase> = purchaseInteractor
                .getPurchases(
                    productType = ProductType.SUBSCRIPTION,
                    // purchaseStatus не задаём — тогда придут ACTIVE и PAUSED.
                )
                .await(
                    operation = "billing.getActiveSubscriptions.await",
                    flowId = flowId
                )

            purchases.filterIsInstance<SubscriptionPurchase>()
        }.mapError(
            operation = "billing.getActiveSubscriptions.mapError",
            flowId = flowId
        ).also { result ->
            result.onSuccess { subscriptions ->
                val preview = subscriptions.take(3).joinToString(separator = "; ") { purchase ->
                    buildString {
                        append("productId=").append(maskValue(purchase.productId.value))
                        append(", orderId=").append(maskValue(purchase.invoiceId.value))
                        append(", purchaseToken=").append(maskValue(purchase.purchaseId.value))
                    }
                }

                val outcome = if (subscriptions.isEmpty()) "EMPTY" else "OK"

                logEnd(
                    operation = "billing.getActiveSubscriptions",
                    flowId = flowId,
                    outcome = outcome,
                    extra = buildString {
                        append("count=").append(subscriptions.size)
                        if (preview.isNotBlank()) {
                            append(", preview=[").append(preview).append("]")
                        }
                    }
                )
            }.onFailure {
                logError(
                    operation = "billing.getActiveSubscriptions",
                    flowId = flowId,
                    outcome = "FAILED",
                    throwable = it
                )
            }
        }
    }

    override fun handleDeeplinkIntent(
        intent: Intent?,
        flowId: String?
    ) {
        logBegin(
            operation = "billing.handleDeeplinkIntent",
            flowId = flowId,
            extra = "intentData=${intent?.data}"
        )

        try {
            intentInteractor.proceedIntent(intent)
            logEnd(
                operation = "billing.handleDeeplinkIntent",
                flowId = flowId,
                outcome = "OK"
            )
        } catch (t: Throwable) {
            logError(
                operation = "billing.handleDeeplinkIntent",
                flowId = flowId,
                outcome = "FAILED",
                throwable = t
            )
            throw t
        }
    }

    // -------------------------------------------------------------
    // ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
    // -------------------------------------------------------------

    /**
     * Обёртка Task<T> → suspend.
     * Task — это Task API RuStore.
     *
     * Логируем только boundary ожидания результата, без лишнего мусора.
     */
    private suspend fun <T> Task<T>.await(
        operation: String,
        flowId: String?
    ): T = suspendCancellableCoroutine { cont ->
        logBegin(operation = operation, flowId = flowId)

        addOnSuccessListener { result ->
            logEnd(
                operation = operation,
                flowId = flowId,
                outcome = "SUCCESS",
                extra = "resultClass=${result?.javaClass?.simpleName}"
            )
            if (cont.isActive) {
                cont.resume(result)
            }
        }

        addOnFailureListener { throwable ->
            logError(
                operation = operation,
                flowId = flowId,
                outcome = "FAILURE",
                throwable = throwable
            )
            if (cont.isActive) {
                cont.resumeWithException(throwable)
            }
        }

        cont.invokeOnCancellation {
            logWarn(
                operation = operation,
                flowId = flowId,
                outcome = "CANCELLED"
            )
            // У Task API нет нормального cancel, просто игнорируем результат.
        }
    }

    /**
     * Маппинг Throwable → BillingException.
     */
    private fun mapToBillingException(
        throwable: Throwable,
        defaultCode: BillingErrorCode = BillingErrorCode.UNKNOWN,
        customMessage: String? = null,
    ): BillingException {
        if (throwable is BillingException) {
            return throwable
        }

        val (code, message) = when (throwable) {
            is RuStoreNotInstalledException -> BillingErrorCode.RUSTORE_NOT_INSTALLED to
                "На устройстве не установлен RuStore"

            is RuStoreOutdatedException -> BillingErrorCode.RUSTORE_OUTDATED to
                "RuStore устарел, требуется обновление"

            is RuStoreApplicationBannedException -> BillingErrorCode.APPLICATION_BANNED to
                "Приложение заблокировано в RuStore"

            is RuStoreUserBannedException -> BillingErrorCode.USER_BANNED to
                "Пользователь заблокирован в RuStore"

            is RuStorePaymentException.RuStorePaymentNetworkException -> BillingErrorCode.NETWORK_ERROR to
                "Ошибка сети при обращении к RuStore: ${throwable.message}"

            is RuStorePaymentException.RuStorePaymentCommonException -> defaultCode to
                (customMessage ?: throwable.message ?: "Ошибка RuStore SDK")

            is RuStorePaymentException -> defaultCode to
                (customMessage ?: throwable.message ?: "Ошибка платежа RuStore")

            else -> defaultCode to (customMessage ?: throwable.message ?: "Неизвестная ошибка биллинга")
        }

        return BillingException(
            code = code,
            message = message,
            cause = throwable
        )
    }

    /**
     * Унифицированное расширение для Result<T>:
     * мапим Throwable в BillingException и сохраняем контекст логирования.
     */
    private fun <T> Result<T>.mapError(
        operation: String,
        flowId: String?,
        extraMapper: ((Throwable) -> BillingException?)? = null
    ): Result<T> {
        return fold(
            onSuccess = { Result.success(it) },
            onFailure = { throwable ->
                val custom = extraMapper?.invoke(throwable)
                val ex = custom ?: mapToBillingException(throwable)

                logDebug(
                    operation = operation,
                    flowId = flowId,
                    extra = "mappedCode=${ex.code}, mappedMessage=${ex.message}"
                )

                Result.failure(ex)
            }
        )
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
     * Маскирование email для логов.
     */
    private fun maskEmail(value: String?): String {
        if (value.isNullOrBlank()) return "null"

        val parts = value.split("@")
        if (parts.size != 2) return "***"

        val local = parts[0]
        val domain = parts[1]

        val localMasked = when {
            local.isEmpty() -> "***"
            local.length == 1 -> "${local.first()}***"
            else -> "${local.first()}***${local.last()}"
        }

        return "$localMasked@$domain"
    }

    /**
     * Унифицированный debug-лог.
     */
    private fun logDebug(
        operation: String,
        flowId: String?,
        extra: String? = null
    ) {
        val message = buildString {
            append("MID")
            append(" op=").append(operation)
            append(" flowId=").append(flowId ?: "null")
            if (!extra.isNullOrBlank()) append(" ").append(extra)
        }
        Log.d(TAG, message)
    }

    /**
     * Унифицированный лог начала шага.
     */
    private fun logBegin(
        operation: String,
        flowId: String?,
        extra: String? = null
    ) {
        val message = buildString {
            append("BEGIN")
            append(" op=").append(operation)
            append(" flowId=").append(flowId ?: "null")
            if (!extra.isNullOrBlank()) append(" ").append(extra)
        }
        Log.d(TAG, message)
    }

    /**
     * Унифицированный лог завершения шага.
     */
    private fun logEnd(
        operation: String,
        flowId: String?,
        outcome: String,
        extra: String? = null
    ) {
        val message = buildString {
            append("END")
            append(" op=").append(operation)
            append(" flowId=").append(flowId ?: "null")
            append(" outcome=").append(outcome)
            if (!extra.isNullOrBlank()) append(" ").append(extra)
        }
        Log.d(TAG, message)
    }

    /**
     * Warning-лог.
     */
    private fun logWarn(
        operation: String,
        flowId: String?,
        outcome: String,
        extra: String? = null
    ) {
        val message = buildString {
            append("END")
            append(" op=").append(operation)
            append(" flowId=").append(flowId ?: "null")
            append(" outcome=").append(outcome)
            if (!extra.isNullOrBlank()) append(" ").append(extra)
        }
        Log.w(TAG, message)
    }

    /**
     * Error-лог.
     */
    private fun logError(
        operation: String,
        flowId: String?,
        outcome: String,
        throwable: Throwable,
        extra: String? = null
    ) {
        val message = buildString {
            append("END")
            append(" op=").append(operation)
            append(" flowId=").append(flowId ?: "null")
            append(" outcome=").append(outcome)
            if (!extra.isNullOrBlank()) append(" ").append(extra)
            append(" errorClass=").append(throwable.javaClass.simpleName)
            append(" errorMessage=").append(throwable.message)
        }
        Log.e(TAG, message, throwable)
    }
}

/**
 * Маппер Product → BillingProduct.
 */
private fun Product.toBillingProduct(): BillingProduct {
    return BillingProduct(
        id = productId.value,
        type = type,
        title = title.value,
        description = description?.value,
        priceLabel = amountLabel.value,
        currency = currency.value,
        isSubscription = type == ProductType.SUBSCRIPTION,
    )
}
