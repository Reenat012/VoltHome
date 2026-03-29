package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.data.billing.pending.PendingConfirmCoordinator
import ru.mugalimov.volthome.data.remote.auth.AuthSession
import ru.mugalimov.volthome.data.remote.yandex.YandexTokenStore
import ru.mugalimov.volthome.data.repository.AuthRepository
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.ui.screens.auth.contract.AuthConfigCheckResult
import ru.mugalimov.volthome.ui.screens.auth.contract.AuthState
import ru.mugalimov.volthome.ui.screens.auth.contract.checkAuthConfig
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val projectsRepo: ProjectsRepository,
    private val yaTokenStore: YandexTokenStore,
    private val pendingCoordinator: PendingConfirmCoordinator
) : ViewModel() {

    // === СЕТЕВОЕ / СЕРВЕРНОЕ СОСТОЯНИЕ АВТОРИЗАЦИИ ===

    sealed interface State {
        object Idle : State
        object Loading : State
        data class Success(val session: AuthSession) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    // === СОСТОЯНИЕ СОГЛАСИЙ (GATE) ===

    private val _consentState = MutableStateFlow(AuthState())
    val consentState: StateFlow<AuthState> = _consentState

    fun loginOptions(): YandexAuthLoginOptions = authRepo.loginOptions()

    /**
     * Стартовая инициализация.
     */
    fun bootstrap() {
        // 0) Проверка конфигурации OAuth (до любых стартов AuthSdkActivity)
        when (val cfg = checkAuthConfig(BuildConfig.YANDEX_CLIENT_ID)) {
            AuthConfigCheckResult.Ok -> _consentState.update { it.copy(configError = null) }
            is AuthConfigCheckResult.Invalid -> _consentState.update { it.copy(configError = cfg.error) }
        }

        viewModelScope.launch {
            val session = authRepo.currentSession()
            if (session != null) {
                _state.value = State.Success(session)
                launch { runCatching { projectsRepo.bootstrapFromRemote() } }
            } else {
                _state.value = State.Idle
            }
        }
    }

    // === СОГЛАСИЯ ===

    fun onTermsAcceptanceChanged(accepted: Boolean) {
        _consentState.update { it.copy(termsAccepted = accepted) }
    }

    fun onPdConsentAcceptanceChanged(accepted: Boolean) {
        _consentState.update { it.copy(pdConsentAccepted = accepted) }
    }

    // === СТАРТ АВТОРИЗАЦИИ (ЖЁСТКИЙ GATE) ===

    fun startLogin() {
        val consent = _consentState.value
        if (!consent.canContinue) {
            if (consent.configError != null) {
                _state.value = State.Error("Ошибка конфигурации входа. Обновите приложение.")
            }
            return
        }

        _consentState.update { it.copy(isLoading = true) }
        _state.value = State.Loading
    }

    fun handleResult(result: YandexAuthResult) {
        viewModelScope.launch {
            // 1) сохранить Я-токен, если есть
            if (result is YandexAuthResult.Success) {
                val yaAccess = runCatching { result.token.value }.getOrNull()
                yaTokenStore.save(yaAccess)
            }

            // 2) серверный логин
            val res = authRepo.handleAuthResult(result)
            _state.value = res.fold(
                onSuccess = { session ->
                    launch { runCatching { projectsRepo.bootstrapFromRemote() } }
                    // 🔥 после успешного логина
                    pendingCoordinator.tryReplay("auth_success")

                    State.Success(session)
                },
                onFailure = { State.Error(mapThrowableToUi(it)) }
            )

            _consentState.update { it.copy(isLoading = false) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepo.signOut()
            yaTokenStore.clear()
            _state.value = State.Idle
            _consentState.value = AuthState()
        }
    }

    private fun mapThrowableToUi(t: Throwable): String {
        val code = t.message?.lowercase().orEmpty()
        return when (code) {
            "cancelled"     -> "Авторизация отменена."
            "connection"    -> "Нет сети. Проверь подключение и повтори."
            "security"      -> "Ошибка конфигурации OAuth. Проверь redirect URI и client_id."
            "oauth_invalid" -> "Неверный/просроченный токен Яндекса. Попробуй снова."
            "jwt_auth"      -> "Не удалось получить серверную сессию. Повтори вход."
            else            -> "Ошибка входа. ${t.message ?: ""}".trim()
        }
    }
}