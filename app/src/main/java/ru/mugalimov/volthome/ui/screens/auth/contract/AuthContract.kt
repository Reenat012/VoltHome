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
    val isLoading: Boolean = false
) {
    val canContinue: Boolean
        get() = termsAccepted && pdConsentAccepted && !isLoading
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