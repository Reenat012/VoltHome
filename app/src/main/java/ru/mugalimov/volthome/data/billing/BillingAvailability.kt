package ru.mugalimov.volthome.data.billing

/**
 * Результат проверки доступности биллинга.
 */
sealed interface BillingAvailability {

    /**
     * Биллинг доступен.
     */
    data object Available : BillingAvailability

    /**
     * Биллинг недоступен.
     */
    data class Unavailable(
        val code: BillingErrorCode,
        val message: String? = null,
        val cause: Throwable? = null
    ) : BillingAvailability
}