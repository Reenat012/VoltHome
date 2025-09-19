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

    /**
     * Автологин на старте:
     * читаем серверную сессию (JWT) из зашифрованного хранилища.
     * Если она валидна — сразу Success (Root/Splash может мгновенно открыть Main).
     */
    fun bootstrap() {
        viewModelScope.launch {
            val session = repo.currentSession()
            _state.value = if (session != null && !session.isExpired) {
                State.Success(session)
            } else {
                State.Idle
            }
        }
    }

    /** Переходим в состояние загрузки перед стартом контракта Яндекса (по кнопке «Войти»). */
    fun startLogin() {
        _state.value = State.Loading
    }

    /**
     * Результат `YandexAuthSdk`: обмениваем успешный результат на серверную сессию.
     * На успех — State.Success с серверным JWT; на ошибку — человекочитаемое сообщение.
     */
    fun handleResult(result: YandexAuthResult) {
        viewModelScope.launch {
            val res = repo.handleAuthResult(result)
            _state.value = res.fold(
                onSuccess = { State.Success(it) },
                onFailure = { State.Error(mapThrowableToUi(it)) }
            )
        }
    }

    /**
     * Выход: инвалидируем сессию на сервере (best-effort) и чистим локальное хранилище.
     * После — возвращаем стейт в Idle, чтобы навигация ушла на Auth.
     */
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
            "cancelled" -> "Авторизация отменена."
            "connection" -> "Нет сети. Проверь подключение и повтори."
            "security" -> "Ошибка конфигурации OAuth. Проверь redirect URI и client_id."
            "oauth_invalid" -> "Неверный/просроченный токен Яндекса. Попробуй снова."
            "jwt_auth" -> "Не удалось получить серверную сессию. Повтори вход."
            else -> "Ошибка входа. ${t.message ?: ""}".trim()
        }
    }
}