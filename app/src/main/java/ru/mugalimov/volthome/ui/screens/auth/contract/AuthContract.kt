package ru.mugalimov.volthome.ui.screens.auth.contract

// Коммит 1 — Явный контракт Auth
// ТОЛЬКО контракт. Без UI. Без ViewModel. Без реализации логики эффектов.

/**
 * STATE
 * Единая точка правды для допуска продолжения.
 */
data class AuthState(
    val termsAccepted: Boolean = false,
    val pdConsentAccepted: Boolean = false,
    val analyticsEnabled: Boolean = false,
    val isReconsent: Boolean = false,
    val requiresPdConsent: Boolean = true,
    val isLoading: Boolean = false,
    val configError: ConfigError? = null
) {
    val canContinueAsGuest: Boolean
        get() = termsAccepted && !isLoading

    val canContinue: Boolean
        get() = termsAccepted && (!requiresPdConsent || pdConsentAccepted) &&
            !isLoading && configError == null

    val canConfirmExistingSession: Boolean
        get() = isReconsent && termsAccepted &&
            (!requiresPdConsent || pdConsentAccepted) && !isLoading
}

/**
 * EVENTS / ACTIONS
 * Пользовательские намерения.
 */
sealed interface AuthEvent {

    data class TermsAcceptanceChanged(
        val accepted: Boolean
    ) : AuthEvent

    data class PdConsentAcceptanceChanged(
        val accepted: Boolean
    ) : AuthEvent

    object ContinueClicked : AuthEvent

    object UserAgreementClicked : AuthEvent
    object PdConsentClicked : AuthEvent
    object PrivacyPolicyClicked : AuthEvent
}

/**
 * EFFECTS
 * Побочные действия (навигация / внешние действия).
 */
sealed interface AuthEffect {

    data class OpenDocument(
        val type: DocumentType
    ) : AuthEffect

    object StartAuth : AuthEffect
}

/**
 * Типы документов.
 * Без текстов и URL.
 */
enum class DocumentType {
    USER_AGREEMENT,
    PD_CONSENT,
    PRIVACY_POLICY
}

/**
 * Ошибки конфигурации OAuth.
 * Без URL/текстов документов.
 */
sealed interface ConfigError {
    object MissingClientId : ConfigError
    object MissingRedirectUri : ConfigError
    data class Other(val debugMessage: String) : ConfigError
}
