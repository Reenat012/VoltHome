package ru.mugalimov.volthome.data.billing

/**
 * Нормализованное исключение billing-слоя.
 */
class BillingException(
    val code: BillingErrorCode,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)