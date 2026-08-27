package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.data.local.auth.LocalAuthProvider
import ru.mugalimov.volthome.data.local.auth.LocalAuthSession
import ru.mugalimov.volthome.data.local.datastore.AnalyticsChoice
import ru.mugalimov.volthome.data.local.datastore.AppPreferences
import ru.mugalimov.volthome.data.repository.AuthRepository
import ru.mugalimov.volthome.ui.screens.auth.contract.AuthConfigCheckResult
import ru.mugalimov.volthome.ui.screens.auth.contract.AuthState
import ru.mugalimov.volthome.ui.screens.auth.contract.checkAuthConfig

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    sealed interface State {
        data object Restoring : State
        data object Idle : State
        data object Loading : State
        data class Success(val session: LocalAuthSession) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Restoring)
    val state: StateFlow<State> = _state

    private val _consentState = MutableStateFlow(AuthState())
    val consentState: StateFlow<AuthState> = _consentState
    private var pendingExistingSession: LocalAuthSession? = null

    fun loginOptions(): YandexAuthLoginOptions = authRepository.loginOptions()

    fun bootstrap() {
        _state.value = State.Restoring
        _consentState.update { it.copy(isLoading = true) }
        when (val config = checkAuthConfig(BuildConfig.YANDEX_CLIENT_ID)) {
            AuthConfigCheckResult.Ok -> _consentState.update { it.copy(configError = null) }
            is AuthConfigCheckResult.Invalid -> _consentState.update { it.copy(configError = config.error) }
        }

        viewModelScope.launch {
            _state.value = runCatching {
                val session = authRepository.currentSession()
                val versions = appPreferences.acceptedLegalVersions.first()
                val analyticsChoice = appPreferences.analyticsChoice.first()
                Triple(session, versions, analyticsChoice)
            }
                .fold(
                    onSuccess = { (session, versions, analyticsChoice) ->
                        val requiresConsent = session != null && (
                            versions.requiresBaseConsent ||
                                (session.provider == LocalAuthProvider.YANDEX &&
                                    versions.requiresPdConsent) ||
                                analyticsChoice == AnalyticsChoice.UNKNOWN
                            )
                        if (requiresConsent) {
                            pendingExistingSession = session
                            _consentState.update {
                                it.copy(
                                    isReconsent = true,
                                    requiresPdConsent = session?.provider == LocalAuthProvider.YANDEX,
                                    analyticsEnabled = analyticsChoice == AnalyticsChoice.ENABLED
                                )
                            }
                            State.Idle
                        } else {
                            session?.let { State.Success(it) } ?: State.Idle
                        }
                    },
                    onFailure = {
                        State.Error(
                            "Не удалось восстановить локальную сессию. " +
                                "Данные не удалены — перезапустите приложение."
                        )
                    }
                )
            _consentState.update { it.copy(isLoading = false) }
        }
    }

    fun onTermsAcceptanceChanged(accepted: Boolean) {
        _consentState.update { it.copy(termsAccepted = accepted) }
    }

    fun onPdConsentAcceptanceChanged(accepted: Boolean) {
        _consentState.update { it.copy(pdConsentAccepted = accepted) }
    }

    fun onAnalyticsAcceptanceChanged(enabled: Boolean) {
        _consentState.update { it.copy(analyticsEnabled = enabled) }
    }

    fun confirmExistingSession() {
        val session = pendingExistingSession ?: return
        val consent = _consentState.value
        if (!consent.canConfirmExistingSession) return
        _consentState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            runCatching {
                persistChoices(includePdConsent = session.provider == LocalAuthProvider.YANDEX)
            }.onSuccess {
                pendingExistingSession = null
                _state.value = State.Success(session)
                _consentState.update { it.copy(isLoading = false, isReconsent = false) }
            }.onFailure {
                _state.value = State.Error("Не удалось сохранить выбор. Повторите ещё раз.")
                _consentState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun continueAsGuest() {
        if (!_consentState.value.canContinueAsGuest) return

        _consentState.update { it.copy(isLoading = true) }
        _state.value = State.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                persistChoices(includePdConsent = false)
                authRepository.continueAsGuest()
            }
                .fold(
                    onSuccess = { State.Success(it) },
                    onFailure = { State.Error("Не удалось запустить гостевой режим.") }
                )
            _consentState.update { it.copy(isLoading = false) }
        }
    }

    fun startLogin() {
        val consent = _consentState.value
        if (!consent.canContinue) {
            if (consent.configError != null) {
                _state.value = State.Error("Вход через Яндекс ID временно недоступен.")
            }
            return
        }

        _consentState.update { it.copy(isLoading = true) }
        _state.value = State.Loading
    }

    fun handleResult(result: YandexAuthResult) {
        viewModelScope.launch {
            val authResult = authRepository.handleAuthResult(result)
            _state.value = authResult.fold(
                onSuccess = {
                    persistChoices(includePdConsent = true)
                    State.Success(it)
                },
                onFailure = { State.Error(mapThrowableToUi(it)) }
            )
            _consentState.update { it.copy(isLoading = false) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.value = State.Loading
            runCatching { authRepository.signOut() }
                .onSuccess {
                    _state.value = State.Idle
                    _consentState.value = AuthState()
                    pendingExistingSession = null
                }
                .onFailure {
                    _state.value = State.Error("Не удалось завершить локальный выход.")
                }
        }
    }

    private fun mapThrowableToUi(error: Throwable): String = when (error.message?.lowercase()) {
        "cancelled" -> "Авторизация отменена."
        "oauth_invalid" -> "Не удалось выполнить вход через Яндекс ID."
        else -> "Ошибка входа через Яндекс ID. ${error.message.orEmpty()}".trim()
    }

    private suspend fun persistChoices(includePdConsent: Boolean) {
        appPreferences.acceptCurrentLegalDocuments(includePdConsent)
        appPreferences.setAnalyticsChoice(
            if (_consentState.value.analyticsEnabled) {
                AnalyticsChoice.ENABLED
            } else {
                AnalyticsChoice.DISABLED
            }
        )
    }
}
