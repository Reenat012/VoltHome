package ru.mugalimov.volthome.data.billing

import android.content.Intent
import android.util.Log
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
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Реализация поверх RuStorePayClient.
 *
 * Требования со стороны окружения:
 *  - в AndroidManifest прописан:
 *      <meta-data
 *          android:name="console_app_id_value"
 *          android:value="@string/rustore_console_app_id" />
 *  - указан sdk_pay_scheme_value и настроены deeplink-и;
 *  - Activity, куда возвращаемся из банковских приложений, вызывает
 *    IntentInteractor.proceedIntent() в onCreate/onNewIntent.
 */
class RustoreBillingManagerImpl @Inject constructor() : RustoreBillingManager {

    companion object {
        private const val TAG = "RustoreBillingManager"
    }

    // берём IO-диспетчер без DI, чтобы не плодить лишние биндинги
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val payClient: RuStorePayClient
        get() {
            Log.d(TAG, "payClient.get() → RuStorePayClient.instance")
            return RuStorePayClient.instance
        }

    private val productInteractor: ProductInteractor
        get() {
            Log.d(TAG, "productInteractor.get()")
            return payClient.getProductInteractor()
        }

    private val purchaseInteractor: PurchaseInteractor
        get() {
            Log.d(TAG, "purchaseInteractor.get()")
            return payClient.getPurchaseInteractor()
        }

    private val userInteractor: UserInteractor
        get() {
            Log.d(TAG, "userInteractor.get()")
            return payClient.getUserInteractor()
        }

    private val intentInteractor: IntentInteractor
        get() {
            Log.d(TAG, "intentInteractor.get()")
            return payClient.getIntentInteractor()
        }

    override suspend fun checkAvailability(): BillingAvailability = withContext(ioDispatcher) {
        Log.d(TAG, "checkAvailability() → start")
        try {
            val result = purchaseInteractor.getPurchaseAvailability().await()
            Log.d(TAG, "checkAvailability() → raw result=$result")

            when (result) {
                is PurchaseAvailabilityResult.Available -> {
                    Log.d(TAG, "checkAvailability() → Available")
                    BillingAvailability.Available
                }

                is PurchaseAvailabilityResult.Unavailable -> {
                    Log.w(
                        TAG,
                        "checkAvailability() → Unavailable, cause=${result.cause?.javaClass?.simpleName}, " +
                                "message=${result.cause?.message}"
                    )
                    val ex = mapToBillingException(
                        throwable = result.cause,
                        defaultCode = BillingErrorCode.BILLING_NOT_AVAILABLE,
                        customMessage = "Платежи недоступны: ${result.cause.message}"
                    )
                    Log.w(TAG, "checkAvailability() → mapped to BillingAvailability.Unavailable(code=${ex.code}, message=${ex.message})")
                    BillingAvailability.Unavailable(
                        code = ex.code,
                        message = ex.message,
                        cause = ex.cause,
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "checkAvailability() → exception: ${t.javaClass.simpleName}: ${t.message}", t)
            val ex = mapToBillingException(
                throwable = t,
                defaultCode = BillingErrorCode.BILLING_NOT_AVAILABLE,
                customMessage = "Не удалось проверить доступность платежей: ${t.message}"
            )
            Log.w(TAG, "checkAvailability() → mapped error to BillingAvailability.Unavailable(code=${ex.code}, message=${ex.message})")
            BillingAvailability.Unavailable(
                code = ex.code,
                message = ex.message,
                cause = ex.cause,
            )
        }
    }

    override suspend fun loadProducts(productIds: List<String>): Result<List<BillingProduct>> =
        withContext(ioDispatcher) {
            Log.d(TAG, "loadProducts() → start, productIds=$productIds")

            if (productIds.isEmpty()) {
                Log.d(TAG, "loadProducts() → productIds empty, returning success(emptyList())")
                return@withContext Result.success(emptyList())
            }

            runCatching {
                val products: List<Product> = productInteractor
                    .getProducts(
                        productsId = productIds.map { ProductId(it) }
                    )
                    .await()

                Log.d(
                    TAG,
                    "loadProducts() → getProducts() success, count=${products.size}, ids=${products.map { it.productId.value }}"
                )

                products.map { it.toBillingProduct() }
            }.mapError { throwable ->
                Log.e(TAG, "loadProducts() → error from SDK: ${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
                null
            }.also { result ->
                result.onSuccess {
                    Log.d(TAG, "loadProducts() → mapped success, count=${it.size}")
                }.onFailure {
                    Log.e(
                        TAG,
                        "loadProducts() → final failure: ${it.javaClass.simpleName}: ${it.message}",
                        it
                    )
                }
            }
        }

    override suspend fun purchaseSubscription(
        productId: String,
        appUserId: String?,
        appUserEmail: String?,
    ): Result<PurchaseCompleted> = withContext(ioDispatcher) {
        Log.d(
            TAG,
            "purchaseSubscription() → start, productId=$productId, appUserId=$appUserId, appUserEmail=$appUserEmail"
        )

        runCatching {
            // Быстрая проверка доступности перед покупкой
            Log.d(TAG, "purchaseSubscription() → checkAvailability() before purchase")
            when (val availability = checkAvailability()) {
                is BillingAvailability.Unavailable -> {
                    Log.w(
                        TAG,
                        "purchaseSubscription() → Billing unavailable before purchase: code=${availability.code}, message=${availability.message}"
                    )
                    throw BillingException(
                        code = availability.code,
                        message = availability.message ?: "Платежи недоступны",
                        cause = availability.cause,
                    )
                }

                BillingAvailability.Available -> {
                    Log.d(TAG, "purchaseSubscription() → BillingAvailable, continue")
                }
            }

            val params = ProductPurchaseParams(
                productId = ProductId(productId),
                // quantity применим только к CONSUMABLE, подписка — 1 шт.
                quantity = null,
                orderId = null,
                developerPayload = null,
                // Эти параметры опциональны, null OK — дока это подтверждает
                appUserId = appUserId?.let { ru.rustore.sdk.pay.model.AppUserId(it) },
                appUserEmail = appUserEmail?.let { ru.rustore.sdk.pay.model.AppUserEmail(it) },
            )

            Log.d(TAG, "purchaseSubscription() → calling purchase() with params=$params")

            val result: ProductPurchaseResult = purchaseInteractor
                .purchase(
                    params = params,
                    preferredPurchaseType = PreferredPurchaseType.ONE_STEP
                )
                .await()

            Log.d(
                TAG,
                "purchaseSubscription() → purchase() success: " +
                        "productId=${result.productId.value}, " +
                        "purchaseId=${result.purchaseId.value}, " +
                        "invoiceId=${result.invoiceId.value}, " +
                        "purchaseType=${result.purchaseType}, " +
                        "productType=${result.productType}, " +
                        "sandbox=${result.sandbox}"
            )

            PurchaseCompleted(
                productId = result.productId.value,
                purchaseId = result.purchaseId.value,
                invoiceId = result.invoiceId.value,
                purchaseType = result.purchaseType,
                productType = result.productType,
                sandbox = result.sandbox,
            )
        }.mapError { throwable ->
            Log.e(
                TAG,
                "purchaseSubscription() → error from SDK: ${throwable.javaClass.simpleName}: ${throwable.message}",
                throwable
            )

            // Отдельно подсвечиваем отмену пользователем
            if (throwable is RuStorePaymentException &&
                throwable.message?.contains("cancel", ignoreCase = true) == true
            ) {
                Log.w(TAG, "purchaseSubscription() → detected user cancel")
                BillingException(
                    code = BillingErrorCode.USER_CANCELLED,
                    message = "Покупка отменена пользователем",
                    cause = throwable,
                )
            } else {
                null
            }
        }.also { result ->
            result.onSuccess {
                Log.d(TAG, "purchaseSubscription() → final success: $it")
            }.onFailure {
                Log.e(
                    TAG,
                    "purchaseSubscription() → final failure: ${it.javaClass.simpleName}: ${it.message}",
                    it
                )
            }
        }
    }

    override suspend fun restorePurchases(): Result<List<Purchase>> =
        withContext(ioDispatcher) {
            Log.d(TAG, "restorePurchases() → start")

            runCatching {
                val purchases = purchaseInteractor
                    .getPurchases()
                    .await()

                Log.d(
                    TAG,
                    "restorePurchases() → getPurchases() success, count=${purchases.size}"
                )

                purchases
            }.mapError { throwable ->
                Log.e(
                    TAG,
                    "restorePurchases() → error from SDK: ${throwable.javaClass.simpleName}: ${throwable.message}",
                    throwable
                )
                null
            }.also { result ->
                result.onSuccess {
                    Log.d(TAG, "restorePurchases() → final success, count=${it.size}")
                }.onFailure {
                    Log.e(
                        TAG,
                        "restorePurchases() → final failure: ${it.javaClass.simpleName}: ${it.message}",
                        it
                    )
                }
            }
        }

    override suspend fun getActiveSubscriptions(): Result<List<SubscriptionPurchase>> =
        withContext(ioDispatcher) {
            Log.d(TAG, "getActiveSubscriptions() → start")

            runCatching {
                val purchases: List<Purchase> = purchaseInteractor
                    .getPurchases(
                        productType = ProductType.SUBSCRIPTION,
                        // purchaseStatus не задаём — тогда придут ACTIVE и PAUSED
                    )
                    .await()

                Log.d(
                    TAG,
                    "getActiveSubscriptions() → getPurchases(SUBSCRIPTION) success, count=${purchases.size}"
                )

                val subs = purchases.filterIsInstance<SubscriptionPurchase>()
                Log.d(
                    TAG,
                    "getActiveSubscriptions() → filtered SubscriptionPurchase, count=${subs.size}"
                )
                subs
            }.mapError { throwable ->
                Log.e(
                    TAG,
                    "getActiveSubscriptions() → error from SDK: ${throwable.javaClass.simpleName}: ${throwable.message}",
                    throwable
                )
                null
            }.also { result ->
                result.onSuccess {
                    Log.d(TAG, "getActiveSubscriptions() → final success, count=${it.size}")
                }.onFailure {
                    Log.e(
                        TAG,
                        "getActiveSubscriptions() → final failure: ${it.javaClass.simpleName}: ${it.message}",
                        it
                    )
                }
            }
        }

    override fun handleDeeplinkIntent(intent: Intent?) {
        Log.d(TAG, "handleDeeplinkIntent() → start, intent=$intent, data=${intent?.data}")
        intentInteractor.proceedIntent(
            intent = intent
        )
        Log.d(TAG, "handleDeeplinkIntent() → proceedIntent() called")
    }

    // -------------------------------------------------------------
    // ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
    // -------------------------------------------------------------

    /**
     * Обёртка Task<T> → suspend.
     * Task — это Task API RuStore (аналог Google Tasks).
     */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        Log.d(TAG, "Task.await() → subscribe")
        addOnSuccessListener { result ->
            Log.d(TAG, "Task.await() → onSuccess: $result")
            if (cont.isActive) {
                cont.resume(result)
            }
        }
        addOnFailureListener { throwable ->
            Log.e(
                TAG,
                "Task.await() → onFailure: ${throwable.javaClass.simpleName}: ${throwable.message}",
                throwable
            )
            if (cont.isActive) {
                cont.resumeWithException(throwable)
            }
        }
        cont.invokeOnCancellation {
            Log.d(TAG, "Task.await() → continuation cancelled")
            // У Task API нет нормального cancel, просто игнорируем результат.
        }
    }

    /**
     * Маппинг Throwable → BillingException по максимально полезным кейсам
     * из документации (RuStoreNotInstalledException, RuStoreOutdatedException и т.п.).
     */
    private fun mapToBillingException(
        throwable: Throwable,
        defaultCode: BillingErrorCode = BillingErrorCode.UNKNOWN,
        customMessage: String? = null,
    ): BillingException {
        // Если это уже наш BillingException — просто пробрасываем.
        if (throwable is BillingException) {
            Log.d(
                TAG,
                "mapToBillingException() → throwable already BillingException(code=${throwable.code}, message=${throwable.message})"
            )
            return throwable
        }

        Log.d(
            TAG,
            "mapToBillingException() → start, throwable=${throwable.javaClass.simpleName}, message=${throwable.message}, defaultCode=$defaultCode, customMessage=$customMessage"
        )

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

        Log.d(TAG, "mapToBillingException() → mapped to code=$code, message=$message")

        return BillingException(
            code = code,
            message = message,
            cause = throwable,
        )
    }

    /**
     * Удобное расширение для Result<T>: мапим любые Throwable в BillingException.
     */
    private fun <T> Result<T>.mapError(
        extraMapper: ((Throwable) -> BillingException?)? = null
    ): Result<T> {
        return this
            .mapCatching { it }
            .onFailure {
                // просто чтобы Result не "забывал" исключение
                Log.d(
                    TAG,
                    "mapError() → onFailure raw throwable: ${it.javaClass.simpleName}: ${it.message}",
                    it
                )
            }
            .fold(
                onSuccess = { value ->
                    Log.d(TAG, "mapError() → onSuccess, value=$value")
                    Result.success(value)
                },
                onFailure = { throwable ->
                    Log.d(
                        TAG,
                        "mapError() → fold.onFailure, throwable=${throwable.javaClass.simpleName}: ${throwable.message}"
                    )
                    val custom = extraMapper?.invoke(throwable)
                    val ex = custom ?: mapToBillingException(throwable)
                    Log.d(
                        TAG,
                        "mapError() → mapped to BillingException(code=${ex.code}, message=${ex.message})"
                    )
                    Result.failure<T>(ex)
                }
            )
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