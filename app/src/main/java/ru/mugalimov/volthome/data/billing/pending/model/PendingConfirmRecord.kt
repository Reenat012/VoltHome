package ru.mugalimov.volthome.data.billing.pending.model

/**
 * Состояние pending confirm записи.
 *
 * Важно:
 * - PENDING / RETRY_WAIT — запись ещё может быть переиграна;
 * - CONFIRMING — сейчас идёт попытка confirm;
 * - CONFIRMED — терминальное успешное состояние, обычно запись сразу удаляется;
 * - TERMINAL_FAILED / TERMINAL_INVALID — запись больше не должна ретраиться.
 */
enum class PendingConfirmState {
    PENDING,
    CONFIRMING,
    RETRY_WAIT,
    CONFIRMED,
    TERMINAL_FAILED,
    TERMINAL_INVALID
}

/**
 * Versioned persistent запись для durable confirm recovery.
 *
 * ВАЖНО:
 * - userId нужен для защиты от replay под другой сессией;
 * - isTerminal — быстрый флаг, чтобы не гонять такую запись дальше;
 * - appVersion полезен для диагностики downgrade/upgrade сценариев.
 */
data class PendingConfirmRecord(
    val pendingStorageVersion: Int = CURRENT_STORAGE_VERSION,
    val purchaseFlowId: String,
    val userId: String,
    val productId: String,
    val orderId: String,
    val purchaseToken: String,
    val createdAt: Long,
    val lastAttemptAt: Long? = null,
    val attemptCount: Int = 0,
    val state: PendingConfirmState = PendingConfirmState.PENDING,
    val lastErrorCode: String? = null,
    val appVersion: String,
    val isTerminal: Boolean = false
) {
    companion object {
        const val CURRENT_STORAGE_VERSION: Int = 1

        /** Максимальное время жизни pending до принудительного перевода в terminal. */
        const val MAX_RECORD_AGE_MS: Long = 7L * 24L * 60L * 60L * 1000L

        /** Бюджет попыток confirm. */
        const val MAX_RETRY_ATTEMPTS: Int = 5

        /** Базовый backoff. Дальше идёт удвоение. */
        const val BASE_BACKOFF_MS: Long = 15_000L
    }

    /**
     * Когда запись можно пробовать снова.
     *
     * attemptCount:
     * 0 -> сразу
     * 1 -> 15 сек
     * 2 -> 30 сек
     * 3 -> 60 сек
     * 4 -> 120 сек
     * 5 -> 240 сек
     */
    fun nextRetryAtMillis(): Long {
        val anchor = lastAttemptAt ?: createdAt
        if (attemptCount <= 0) return anchor

        val multiplier = 1L shl (attemptCount - 1).coerceAtLeast(0)
        val delay = BASE_BACKOFF_MS * multiplier
        return anchor + delay
    }

    /**
     * Можно ли ещё пытаться confirm по бюджету и возрасту записи.
     */
    fun canRetry(nowMillis: Long): Boolean {
        if (isTerminal) return false
        if (state == PendingConfirmState.CONFIRMED) return false
        if (attemptCount >= MAX_RETRY_ATTEMPTS) return false
        if (nowMillis - createdAt > MAX_RECORD_AGE_MS) return false
        return nowMillis >= nextRetryAtMillis()
    }

    /**
     * Удобный перевод в terminal-invalid.
     */
    fun asTerminalInvalid(
        errorCode: String
    ): PendingConfirmRecord {
        return copy(
            state = PendingConfirmState.TERMINAL_INVALID,
            lastErrorCode = errorCode,
            isTerminal = true
        )
    }

    /**
     * Удобный перевод в terminal-failed.
     */
    fun asTerminalFailed(
        errorCode: String?
    ): PendingConfirmRecord {
        return copy(
            state = PendingConfirmState.TERMINAL_FAILED,
            lastErrorCode = errorCode,
            isTerminal = true
        )
    }
}