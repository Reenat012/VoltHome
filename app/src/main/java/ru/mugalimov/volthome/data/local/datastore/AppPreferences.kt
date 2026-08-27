package ru.mugalimov.volthome.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.UserPlan

private val Context.voltHomePreferencesDataStore by preferencesDataStore("volthome_prefs")

class AppPreferences(context: Context) {

    private val dataStore = context.applicationContext.voltHomePreferencesDataStore

    private companion object {
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val PHASE_MODE = stringPreferencesKey("phase_mode")
        val USER_PLAN = stringPreferencesKey("user_plan")
        val USER_PLAN_UNTIL = longPreferencesKey("user_plan_until")
        val ANALYTICS_CHOICE = stringPreferencesKey("analytics_choice")
        val ACCEPTED_AGREEMENT_VERSION = intPreferencesKey("accepted_agreement_version")
        val ACCEPTED_PRIVACY_VERSION = intPreferencesKey("accepted_privacy_version")
        val ACCEPTED_PD_CONSENT_VERSION = intPreferencesKey("accepted_pd_consent_version")
    }

    val ifFirstLaunch: Flow<Boolean> = dataStore.data
        .map { it[IS_FIRST_LAUNCH] ?: true }

    val phaseMode: Flow<PhaseMode> = dataStore.data
        .map { pref ->
            when (pref[PHASE_MODE]) {
                PhaseMode.SINGLE.name -> PhaseMode.SINGLE
                PhaseMode.THREE.name -> PhaseMode.THREE
                else -> PhaseMode.THREE // дефолт: 3 фазы
            }
        }

    val userPlan: Flow<UserPlan> = dataStore.data.map { prefs ->
        UserPlan(
            plan = prefs[USER_PLAN]?.takeIf { it.isNotBlank() } ?: "free",
            planUntilEpochSeconds = prefs[USER_PLAN_UNTIL]
        )
    }

    /**
     * Аналитика запрещена, пока пользователь явно не выбрал иной вариант.
     * UNKNOWN отличается от DISABLED: первый вариант означает, что вопрос ещё не задавался.
     */
    val analyticsChoice: Flow<AnalyticsChoice> = dataStore.data.map { prefs ->
        runCatching {
            AnalyticsChoice.valueOf(prefs[ANALYTICS_CHOICE].orEmpty())
        }.getOrDefault(AnalyticsChoice.UNKNOWN)
    }

    val acceptedLegalVersions: Flow<AcceptedLegalVersions> = dataStore.data.map { prefs ->
        AcceptedLegalVersions(
            agreement = prefs[ACCEPTED_AGREEMENT_VERSION] ?: 0,
            privacy = prefs[ACCEPTED_PRIVACY_VERSION] ?: 0,
            pdConsent = prefs[ACCEPTED_PD_CONSENT_VERSION] ?: 0
        )
    }

    suspend fun setFirstLaunchCompeted() {
        dataStore.edit { it[IS_FIRST_LAUNCH] = false }
    }

    suspend fun setPhaseMode(mode: PhaseMode) {
        dataStore.edit { it[PHASE_MODE] = mode.name }
    }

    suspend fun setUserPlan(plan: UserPlan) {
        dataStore.edit { prefs ->
            prefs[USER_PLAN] = plan.plan
            val until = plan.planUntilEpochSeconds
            if (until == null) prefs.remove(USER_PLAN_UNTIL)
            else prefs[USER_PLAN_UNTIL] = until
        }
    }

    suspend fun setAnalyticsChoice(choice: AnalyticsChoice) {
        dataStore.edit { it[ANALYTICS_CHOICE] = choice.name }
    }

    suspend fun acceptCurrentLegalDocuments(includePdConsent: Boolean) {
        dataStore.edit { prefs ->
            prefs[ACCEPTED_AGREEMENT_VERSION] = LegalDocumentVersions.AGREEMENT
            prefs[ACCEPTED_PRIVACY_VERSION] = LegalDocumentVersions.PRIVACY
            if (includePdConsent) {
                prefs[ACCEPTED_PD_CONSENT_VERSION] = LegalDocumentVersions.PD_CONSENT
            }
        }
    }
}

enum class AnalyticsChoice {
    UNKNOWN,
    ENABLED,
    DISABLED
}

data class AcceptedLegalVersions(
    val agreement: Int,
    val privacy: Int,
    val pdConsent: Int
) {
    val requiresBaseConsent: Boolean
        get() = agreement < LegalDocumentVersions.AGREEMENT ||
            privacy < LegalDocumentVersions.PRIVACY

    val requiresPdConsent: Boolean
        get() = pdConsent < LegalDocumentVersions.PD_CONSENT
}

/** Увеличивать при существенном изменении соответствующего документа. */
object LegalDocumentVersions {
    const val AGREEMENT = 3
    const val PRIVACY = 3
    const val PD_CONSENT = 3
}
