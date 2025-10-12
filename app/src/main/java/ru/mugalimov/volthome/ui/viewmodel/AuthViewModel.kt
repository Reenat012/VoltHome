package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yandex.authsdk.YandexAuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.mugalimov.volthome.data.remote.auth.AuthSession
import ru.mugalimov.volthome.data.repository.AuthRepository
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.data.remote.yandex.YandexTokenStore // ← NEW
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val projectsRepo: ProjectsRepository,
    private val yaTokenStore: YandexTokenStore // ← NEW
) : ViewModel() {

    sealed interface State {
        object Idle : State
        object Loading : State
        data class Success(val session: AuthSession) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun bootstrap() {
        viewModelScope.launch {
            val session = authRepo.currentSession()
            if (session != null && !session.isExpired) {
                _state.value = State.Success(session)
                launch { runCatching { projectsRepo.bootstrapFromRemote() } }
            } else {
                _state.value = State.Idle
            }
        }
    }

    fun startLogin() { _state.value = State.Loading }

    fun handleResult(result: YandexAuthResult) {
        viewModelScope.launch {
            // 1) Сохраним access_token из результата SDK (если есть)
            if (result is YandexAuthResult.Success) {
                // SDK v2: result.token.value; на старых может отличаться — защищаемся
                val yaAccess = runCatching { result.token.value }.getOrNull()
                yaTokenStore.save(yaAccess)
            }

            // 2) Дальше — как было
            val res = authRepo.handleAuthResult(result)
            _state.value = res.fold(
                onSuccess = { session ->
                    launch { runCatching { projectsRepo.bootstrapFromRemote() } }
                    State.Success(session)
                },
                onFailure = { State.Error(mapThrowableToUi(it)) }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepo.signOut()
            yaTokenStore.clear() // ← NEW: чистим Я-токен
            _state.value = State.Idle
        }
    }

    private fun mapThrowableToUi(t: Throwable): String {
        val code = t.message?.lowercase().orEmpty()
        return when (code) {
            "cancelled" -> "Авторизация отменена."
            "connection" -> "Нет сети. Проверь подключение и повтори."
            "security" -> "Ошибка конфигурации OAuth. Проверь redirect URI и client_id."
            "oauth_invalid" -> "Неверный/просроченный токен Яндекса. Попробуй снова."
            "jwt_auth" -> "Не удалось получить серверную сессию. Повтори вход."
            else -> "Ошибка входа. ${t.message ?: ""}".trim()
        }
    }
}