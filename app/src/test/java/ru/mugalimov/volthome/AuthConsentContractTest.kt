package ru.mugalimov.volthome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.ui.screens.auth.contract.AuthState
import ru.mugalimov.volthome.data.local.datastore.AcceptedLegalVersions
import ru.mugalimov.volthome.data.local.datastore.LegalDocumentVersions

class AuthConsentContractTest {

    @Test
    fun `guest mode requires agreement but not Yandex consent`() {
        val state = AuthState(
            termsAccepted = true,
            pdConsentAccepted = false
        )

        assertTrue(state.canContinueAsGuest)
        assertFalse(state.canContinue)
    }

    @Test
    fun `Yandex sign in requires both agreement and separate consent`() {
        val state = AuthState(
            termsAccepted = true,
            pdConsentAccepted = true
        )

        assertTrue(state.canContinueAsGuest)
        assertTrue(state.canContinue)
    }

    @Test
    fun `loading blocks both entry methods`() {
        val state = AuthState(
            termsAccepted = true,
            pdConsentAccepted = true,
            isLoading = true
        )

        assertFalse(state.canContinueAsGuest)
        assertFalse(state.canContinue)
    }

    @Test
    fun `material document version update requires repeated consent`() {
        val stale = AcceptedLegalVersions(
            agreement = LegalDocumentVersions.AGREEMENT - 1,
            privacy = LegalDocumentVersions.PRIVACY,
            pdConsent = LegalDocumentVersions.PD_CONSENT - 1
        )
        val current = AcceptedLegalVersions(
            agreement = LegalDocumentVersions.AGREEMENT,
            privacy = LegalDocumentVersions.PRIVACY,
            pdConsent = LegalDocumentVersions.PD_CONSENT
        )

        assertTrue(stale.requiresBaseConsent)
        assertTrue(stale.requiresPdConsent)
        assertFalse(current.requiresBaseConsent)
        assertFalse(current.requiresPdConsent)
    }

    @Test
    fun `existing Yandex session cannot continue before repeated consent`() {
        val state = AuthState(
            isReconsent = true,
            requiresPdConsent = true,
            termsAccepted = true,
            pdConsentAccepted = false
        )

        assertFalse(state.canConfirmExistingSession)
        assertTrue(state.copy(pdConsentAccepted = true).canConfirmExistingSession)
    }
}
