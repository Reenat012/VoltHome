package ru.mugalimov.volthome.data.billing

/**
 * Нормализованные коды ошибок биллинга.
 */
enum class BillingErrorCode {
    USER_CANCELLED,
    RUSTORE_NOT_INSTALLED,
    RUSTORE_OUTDATED,
    APPLICATION_BANNED,
    USER_BANNED,
    MONETIZATION_DISABLED_OR_COMPANY_PROBLEM,
    NETWORK_ERROR,
    BILLING_NOT_AVAILABLE,
    UNKNOWN
}