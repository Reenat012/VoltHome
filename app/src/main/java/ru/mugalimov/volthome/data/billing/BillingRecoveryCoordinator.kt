package ru.mugalimov.volthome.data.billing

import android.util.Log
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinator для recovery-путей биллинга.
 *
 * Задачи:
 * - не давать pending replay и restore лезть параллельно без контроля;
 * - не давать status refresh идти во время replay/restore;
 * - не давать одной purchase identity одновременно участвовать
 *   в двух recovery-контурах.
 *
 * Это именно coordinator оркестрации, а не storage.
 */
@Singleton
class BillingRecoveryCoordinator @Inject constructor() {

    companion object {
        private const val TAG = "BillingRecoveryCoord"
    }

    private val lock = Any()

    /** Идёт ли сейчас pending replay. */
    private var pendingReplayActive: Boolean = false

    /** Идёт ли сейчас restore. */
    private var restoreActive: Boolean = false

    /** Идёт ли сейчас status refresh. */
    private var statusRefreshActive: Boolean = false

    /** Идёт ли сейчас login recovery. */
    private var loginRecoveryActive: Boolean = false

    /**
     * Карта занятых purchase identity.
     * key   = детерминированная identity покупки
     * value = кто сейчас владеет этой identity
     */
    private val identityOwners = mutableMapOf<String, String>()

    /**
     * Начать pending replay.
     *
     * replay выше restore и выше status refresh.
     */
    fun beginPendingReplay(reason: String): Boolean = synchronized(lock) {
        if (pendingReplayActive) {
            Log.w(TAG, "PENDING_REPLAY_REJECTED_ALREADY_ACTIVE reason=$reason")
            return false
        }

        if (restoreActive) {
            Log.w(TAG, "PENDING_REPLAY_REJECTED_RESTORE_ACTIVE reason=$reason")
            return false
        }

        pendingReplayActive = true
        Log.d(TAG, "PENDING_REPLAY_BEGIN reason=$reason")
        true
    }

    /**
     * Завершить pending replay.
     */
    fun endPendingReplay(reason: String) = synchronized(lock) {
        pendingReplayActive = false
        Log.d(TAG, "PENDING_REPLAY_END reason=$reason")
    }

    /**
     * Начать restore.
     *
     * restore нельзя запускать, пока идёт pending replay.
     */
    fun beginRestore(flowId: String, reason: String): Boolean = synchronized(lock) {
        if (restoreActive) {
            Log.w(TAG, "RESTORE_REJECTED_ALREADY_ACTIVE flowId=$flowId reason=$reason")
            return false
        }

        if (pendingReplayActive) {
            Log.w(TAG, "RESTORE_REJECTED_PENDING_REPLAY_ACTIVE flowId=$flowId reason=$reason")
            return false
        }

        if (loginRecoveryActive) {
            Log.w(TAG, "RESTORE_REJECTED_LOGIN_RECOVERY_ACTIVE flowId=$flowId reason=$reason")
            return false
        }

        restoreActive = true
        Log.d(TAG, "RESTORE_BEGIN flowId=$flowId reason=$reason")
        true
    }

    /**
     * Завершить restore.
     */
    fun endRestore(flowId: String, reason: String) = synchronized(lock) {
        restoreActive = false
        Log.d(TAG, "RESTORE_END flowId=$flowId reason=$reason")
    }

    /**
     * Начать status refresh.
     *
     * status refresh не должен идти,
     * если уже активен pending replay или restore.
     */
    fun beginStatusRefresh(flowId: String, reason: String): Boolean = synchronized(lock) {
        if (statusRefreshActive) {
            Log.w(TAG, "STATUS_REFRESH_REJECTED_ALREADY_ACTIVE flowId=$flowId reason=$reason")
            return false
        }

        if (pendingReplayActive) {
            Log.w(TAG, "STATUS_REFRESH_REJECTED_PENDING_REPLAY_ACTIVE flowId=$flowId reason=$reason")
            return false
        }

        if (restoreActive) {
            Log.w(TAG, "STATUS_REFRESH_REJECTED_RESTORE_ACTIVE flowId=$flowId reason=$reason")
            return false
        }

        if (loginRecoveryActive) {
            Log.w(TAG, "STATUS_REFRESH_REJECTED_LOGIN_RECOVERY_ACTIVE flowId=$flowId reason=$reason")
            return false
        }

        statusRefreshActive = true
        Log.d(TAG, "STATUS_REFRESH_BEGIN flowId=$flowId reason=$reason")
        true
    }

    /**
     * Начать login recovery.
     *
     * login recovery идёт после pending replay и не должен пересекаться
     * с restore/status refresh.
     */
    fun beginLoginRecovery(reason: String): Boolean = synchronized(lock) {
        if (loginRecoveryActive) {
            Log.w(TAG, "LOGIN_RECOVERY_REJECTED_ALREADY_ACTIVE reason=$reason")
            return false
        }

        if (pendingReplayActive) {
            Log.w(TAG, "LOGIN_RECOVERY_REJECTED_PENDING_REPLAY_ACTIVE reason=$reason")
            return false
        }

        if (restoreActive) {
            Log.w(TAG, "LOGIN_RECOVERY_REJECTED_RESTORE_ACTIVE reason=$reason")
            return false
        }

        if (statusRefreshActive) {
            Log.w(TAG, "LOGIN_RECOVERY_REJECTED_STATUS_REFRESH_ACTIVE reason=$reason")
            return false
        }

        loginRecoveryActive = true
        Log.d(TAG, "LOGIN_RECOVERY_BEGIN reason=$reason")
        true
    }

    /**
     * Завершить login recovery.
     */
    fun endLoginRecovery(reason: String) = synchronized(lock) {
        loginRecoveryActive = false
        Log.d(TAG, "LOGIN_RECOVERY_END reason=$reason")
    }

    /**
     * Завершить status refresh.
     */
    fun endStatusRefresh(flowId: String, reason: String) = synchronized(lock) {
        statusRefreshActive = false
        Log.d(TAG, "STATUS_REFRESH_END flowId=$flowId reason=$reason")
    }

    /**
     * Захватить purchase identity.
     *
     * Нельзя, чтобы одна и та же identity одновременно шла:
     * - и через pending replay
     * - и через restore reconcile
     */
    fun tryAcquireIdentity(
        productId: String,
        orderId: String,
        purchaseToken: String,
        owner: String
    ): Boolean = synchronized(lock) {
        val identity = buildPurchaseIdentity(productId, orderId, purchaseToken)
        val currentOwner = identityOwners[identity]

        if (currentOwner != null && currentOwner != owner) {
            Log.w(
                TAG,
                "IDENTITY_REJECTED identity=${mask(identity)} owner=$owner currentOwner=$currentOwner"
            )
            return false
        }

        identityOwners[identity] = owner
        Log.d(TAG, "IDENTITY_ACQUIRED identity=${mask(identity)} owner=$owner")
        true
    }

    /**
     * Освободить purchase identity.
     */
    fun releaseIdentity(
        productId: String,
        orderId: String,
        purchaseToken: String,
        owner: String
    ) = synchronized(lock) {
        val identity = buildPurchaseIdentity(productId, orderId, purchaseToken)
        val currentOwner = identityOwners[identity]

        if (currentOwner == owner) {
            identityOwners.remove(identity)
            Log.d(TAG, "IDENTITY_RELEASED identity=${mask(identity)} owner=$owner")
        } else {
            Log.w(
                TAG,
                "IDENTITY_RELEASE_SKIPPED identity=${mask(identity)} owner=$owner currentOwner=$currentOwner"
            )
        }
    }

    /**
     * Техническая проверка: идёт ли сейчас recovery.
     */
    fun hasActiveRecovery(): Boolean = synchronized(lock) {
        pendingReplayActive || restoreActive || loginRecoveryActive
    }

    /**
     * Жёсткий runtime reset на logout.
     *
     * Нужен, чтобы новый пользователь не унаследовал:
     * - флаги активного replay/restore/login recovery;
     * - блокировки status refresh;
     * - purchase identity locks.
     *
     * Это именно cleanup runtime-состояния, а не graceful shutdown операций.
     */
    fun resetRuntimeState(reason: String) = synchronized(lock) {
        pendingReplayActive = false
        restoreActive = false
        statusRefreshActive = false
        loginRecoveryActive = false
        identityOwners.clear()

        Log.w(TAG, "RUNTIME_RESET reason=$reason")
    }

    /**
     * Детерминированная identity покупки.
     */
    private fun buildPurchaseIdentity(
        productId: String,
        orderId: String,
        purchaseToken: String
    ): String {
        val raw = "$productId|$orderId|$purchaseToken"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))

        return digest.joinToString(separator = "") {
            "%02x".format(Locale.US, it)
        }
    }

    private fun mask(value: String?): String {
        if (value.isNullOrBlank()) return "null"
        return when {
            value.length <= 8 -> "***${value.takeLast(2)}"
            else -> "${value.take(3)}***${value.takeLast(4)}"
        }
    }
}