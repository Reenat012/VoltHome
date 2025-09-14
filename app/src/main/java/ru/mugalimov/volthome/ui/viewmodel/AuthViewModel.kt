package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yandex.authsdk.YandexAuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.remote.auth.AuthSession
import ru.mugalimov.volthome.data.repository.AuthRepository
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository
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
            val logged = repo.isLoggedIn()
            if (logged) {
                val s = repo.currentSession()
                if (s != null && !s.isExpired) {
                    _state.value = State.Success(s)
                    return@launch
                }
            }
            _state.value = State.Idle
        }
    }

    fun startLogin() {
        _state.value = State.Loading
    }

    fun handleResult(result: YandexAuthResult) {
        viewModelScope.launch {
            val res = repo.handleAuthResult(result)
            _state.value = res.fold(
                onSuccess = { State.Success(it) },
                onFailure = { State.Error(mapThrowableToUi(it)) }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut()
            _state.value = State.Idle
        }
    }

    private fun mapThrowableToUi(t: Throwable): String {
        // repo в failure возвращает RuntimeException с коротким кодом:
        // "cancelled", "connection", "security", "oauth_invalid", "jwt_auth", "other"
        val code = t.message?.lowercase().orEmpty()
        return when (code) {
            "cancelled" -> "Авторизация отменена"
            "connection" -> "Нет сети. Проверьте подключение и повторите."
            "security" -> "Ошибка конфигурации OAuth. Проверьте Redirect URI и client_id."
            "oauth_invalid" -> "Неверный или просроченный токен. Попробуйте войти снова."
            "jwt_auth" -> "Ошибка авторизации (JWT). Попробуйте повторить вход."
            else -> "Ошибка входа. ${t.message ?: ""}".trim()
        }
    }
}