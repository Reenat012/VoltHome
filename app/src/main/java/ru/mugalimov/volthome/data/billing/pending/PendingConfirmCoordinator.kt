package ru.mugalimov.volthome.data.billing.pending

import android.util.Log
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.data.billing.BillingRecoveryCoordinator
import ru.mugalimov.volthome.data.billing.pending.model.PendingConfirmRecord
import ru.mugalimov.volthome.data.billing.pending.model.PendingConfirmState
import ru.mugalimov.volthome.data.billing.pending.storage.PendingConfirmStorage
import ru.mugalimov.volthome.data.remote.auth.SessionManager
import ru.mugalimov.volthome.data.repository.SubscriptionRepository

@Singleton
class PendingConfirmCoordinator @Inject constructor(
    private val storage: PendingConfirmStorage,
    private val sessionManager: SessionManager,
    private val subscriptionRepository: SubscriptionRepository,
    private val billingRecoveryCoordinator: BillingRecoveryCoordinator
) {

    companion object {
        private const val TAG = "PendingConfirmCoord"

        /**
         * fallback на случай, если uid по какой-то причине не прочитался.
         * Идеально этого не должно быть, но запись лучше сохранить, чем потерять.
         */
        private const val UNKNOWN_USER_ID = "__missing_uid__"
    }

    /**
     * Владелец identity для replay-контура.
     */
    private fun replayOwner(flowId: String): String = "pending_replay:$flowId"

    private val replayMutex = Mutex()

    /**
     * Создание persistent pending сразу после SDK success.
     * Это самая критичная точка всего коммита.
     */
    suspend fun onSdkSuccess(
        flowId: String,
        productId: String,
        orderId: String,
        purchaseToken: String
    ) {
        if (!BuildConfig.BILLING_PENDING_CONFIRM_ENABLED) {
            Log.w(TAG, "SDK_SUCCESS_SKIPPED featureFlag=false flowId=$flowId")
            return
        }

        val userId = sessionManager.currentUidOrNull() ?: UNKNOWN_USER_ID
        val now = System.currentTimeMillis()

        val record = PendingConfirmRecord(
            purchaseFlowId = flowId,
            userId = userId,
            productId = productId,
            orderId = orderId,
            purchaseToken = purchaseToken,
            createdAt = now,
            lastAttemptAt = null,
            attemptCount = 0,
            state = PendingConfirmState.PENDING,
            lastErrorCode = null,
            appVersion = BuildConfig.VERSION_NAME,
            isTerminal = false
        )

        storage.upsert(record)

        Log.d(
            TAG,
            "SDK_SUCCESS_STORED flowId=$flowId userId=$userId productId=${mask(productId)} orderId=${mask(orderId)}"
        )
    }

    /**
     * Успешный confirm — запись удаляем.
     */
    suspend fun onConfirmSuccess(flowId: String) {
        if (!BuildConfig.BILLING_PENDING_CONFIRM_ENABLED) return

        storage.removeByFlowId(flowId)
        Log.d(TAG, "CONFIRM_SUCCESS_REMOVED flowId=$flowId")
    }

    /**
     * Неуспешный confirm — обновляем retry метаданные.
     */
    suspend fun onConfirmFailed(
        flowId: String,
        error: Throwable
    ) {
        if (!BuildConfig.BILLING_PENDING_CONFIRM_ENABLED) return

        val current = storage.getByFlowId(flowId)
        if (current == null) {
            Log.w(TAG, "CONFIRM_FAILED_BUT_RECORD_MISSING flowId=$flowId")
            return
        }

        val now = System.currentTimeMillis()
        val nextAttempt = current.attemptCount + 1
        val errorCode = classifyError(error)

        val updated = if (!isRetryable(error) || nextAttempt >= PendingConfirmRecord.MAX_RETRY_ATTEMPTS) {
            current.copy(
                lastAttemptAt = now,
                attemptCount = nextAttempt,
                state = PendingConfirmState.TERMINAL_FAILED,
                lastErrorCode = errorCode,
                isTerminal = true
            )
        } else {
            current.copy(
                lastAttemptAt = now,
                attemptCount = nextAttempt,
                state = PendingConfirmState.RETRY_WAIT,
                lastErrorCode = errorCode,
                isTerminal = false
            )
        }

        storage.upsert(updated)

        Log.w(
            TAG,
            "CONFIRM_FAILED_UPDATED flowId=$flowId retryable=${isRetryable(error)} attempts=${updated.attemptCount} state=${updated.state} errorCode=$errorCode"
        )
    }

    /**
     * Очистка pending-хвоста при logout.
     *
     * Важно:
     * - чистим только user scope текущего пользователя;
     * - при необходимости заодно убираем fallback unknown-user записи,
     *   чтобы новый логин не унаследовал старый pending хвост.
     */
    suspend fun clearForLogout(userId: String?) {
        if (!BuildConfig.BILLING_PENDING_CONFIRM_ENABLED) {
            Log.w(TAG, "LOGOUT_CLEAR_SKIPPED featureFlag=false")
            return
        }

        if (userId.isNullOrBlank()) {
            // Если uid уже потерян, делаем best-effort очистку только unknown bucket.
            Log.w(TAG, "LOGOUT_CLEAR_NO_UID")
            storage.removeByUserId(
                userId = UNKNOWN_USER_ID,
                includeUnknownUser = true
            )
            return
        }

        storage.removeByUserId(
            userId = userId,
            includeUnknownUser = true
        )

        Log.w(TAG, "LOGOUT_CLEAR_DONE userId=$userId")
    }

    /**
     * Replay при старте / логине / cold start.
     *
     * Важно:
     * - single-flight на coordinator уровне;
     * - терминальные записи не трогаем;
     * - записи другого userId не переигрываем;
     * - stale записи переводим в terminal-failed.
     */
    suspend fun tryReplay(reason: String) {
        if (!BuildConfig.BILLING_PENDING_CONFIRM_ENABLED) {
            Log.w(TAG, "REPLAY_SKIPPED featureFlag=false reason=$reason")
            return
        }

        replayMutex.withLock {
            // 🔥 replay имеет приоритет над restore/status refresh.
            if (!billingRecoveryCoordinator.beginPendingReplay(reason)) {
                Log.w(TAG, "REPLAY_SKIPPED_COORDINATOR_REJECT reason=$reason")
                return@withLock
            }

            try {
                val now = System.currentTimeMillis()
                val currentUid = sessionManager.currentUidOrNull()
                val records = storage.getAll().sortedBy { it.createdAt }

                Log.d(
                    TAG,
                    "REPLAY_BEGIN reason=$reason currentUid=$currentUid records=${records.size}"
                )

                records.forEach { record ->
                    // Терминальные записи не переигрываем.
                    if (record.isTerminal) {
                        return@forEach
                    }

                    // Просроченные записи переводим в terminal-failed.
                    if (now - record.createdAt > PendingConfirmRecord.MAX_RECORD_AGE_MS) {
                        storage.upsert(
                            record.asTerminalFailed(errorCode = "STALE_PENDING")
                        )
                        Log.w(TAG, "REPLAY_STALE_TO_TERMINAL flowId=${record.purchaseFlowId}")
                        return@forEach
                    }

                    // Если запись привязана к другому пользователю — не трогаем.
                    if (
                        currentUid != null &&
                        record.userId != UNKNOWN_USER_ID &&
                        record.userId != currentUid
                    ) {
                        Log.w(
                            TAG,
                            "REPLAY_SKIPPED_USER_MISMATCH flowId=${record.purchaseFlowId} recordUser=${record.userId} currentUser=$currentUid"
                        )
                        return@forEach
                    }

                    // Если ещё не пришло время retry — пропускаем.
                    if (!record.canRetry(now)) {
                        Log.d(
                            TAG,
                            "REPLAY_SKIPPED_BACKOFF flowId=${record.purchaseFlowId} nextRetryAt=${record.nextRetryAtMillis()}"
                        )
                        return@forEach
                    }

                    // 🔥 Нельзя, чтобы одна identity одновременно пошла
                    // и через pending replay, и через другой recovery-контур.
                    val owner = replayOwner(record.purchaseFlowId)
                    val identityAcquired = billingRecoveryCoordinator.tryAcquireIdentity(
                        productId = record.productId,
                        orderId = record.orderId,
                        purchaseToken = record.purchaseToken,
                        owner = owner
                    )

                    if (!identityAcquired) {
                        Log.w(
                            TAG,
                            "REPLAY_SKIPPED_IDENTITY_BUSY flowId=${record.purchaseFlowId} reason=$reason"
                        )
                        return@forEach
                    }

                    try {
                        // Перед вызовом confirm фиксируем состояние CONFIRMING.
                        storage.upsert(
                            record.copy(
                                state = PendingConfirmState.CONFIRMING,
                                lastAttemptAt = now
                            )
                        )


                        // 🔥 ТЕСТОВОЕ ОКНО:
                        // даём время руками нажать manual restore, пока pending replay ещё активен.
                        Log.d(
                            TAG,
                            "REPLAY_TEST_WINDOW_OPEN flowId=${record.purchaseFlowId} reason=$reason"
                        )
                        delay(10_000)

                        val result = subscriptionRepository.confirmRustorePurchase(
                            productId = record.productId,
                            orderId = record.orderId,
                            purchaseToken = record.purchaseToken,
                            flowId = record.purchaseFlowId,
                            source = "pending_replay"
                        )

                        result.fold(
                            onSuccess = {
                                onConfirmSuccess(record.purchaseFlowId)
                                Log.d(
                                    TAG,
                                    "REPLAY_CONFIRM_OK flowId=${record.purchaseFlowId} reason=$reason"
                                )
                            },
                            onFailure = { error ->
                                onConfirmFailed(record.purchaseFlowId, error)
                                Log.w(
                                    TAG,
                                    "REPLAY_CONFIRM_FAILED flowId=${record.purchaseFlowId} reason=$reason errorClass=${error.javaClass.simpleName}"
                                )
                            }
                        )
                    } finally {
                        billingRecoveryCoordinator.releaseIdentity(
                            productId = record.productId,
                            orderId = record.orderId,
                            purchaseToken = record.purchaseToken,
                            owner = owner
                        )
                    }
                }

                Log.d(TAG, "REPLAY_END reason=$reason")
            } finally {
                billingRecoveryCoordinator.endPendingReplay(reason)
            }
        }
    }

    private fun isRetryable(error: Throwable): Boolean {
        return when (error) {
            is java.io.IOException -> true
            is HttpException -> {
                val code = error.code()
                code >= 500 || code == 408 || code == 409 || code == 429
            }
            else -> {
                val message = error.message.orEmpty().lowercase()
                "timeout" in message ||
                        "network" in message ||
                        "connection" in message ||
                        "tempor" in message ||
                        "unavailable" in message
            }
        }
    }

    private fun classifyError(error: Throwable): String {
        return when (error) {
            is java.net.SocketTimeoutException -> "SOCKET_TIMEOUT"
            is java.io.IOException -> "IO_EXCEPTION"
            is HttpException -> "HTTP_${error.code()}"
            else -> error.javaClass.simpleName.ifBlank { "UNKNOWN" }
        }
    }

    private fun mask(value: String?): String {
        if (value.isNullOrBlank()) return "null"
        return when {
            value.length <= 4 -> "***$value"
            value.length <= 8 -> "${value.take(1)}***${value.takeLast(2)}"
            else -> "${value.take(3)}***${value.takeLast(4)}"
        }
    }
}