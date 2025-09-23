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
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val projectsRepo: ProjectsRepository
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
     * - читаем серверную сессию (JWT) из зашифрованного хранилища
     * - если валидна — сразу Success (Root/Splash может открыть Main)
     * - ПАРАЛЛЕЛЬНО запускаем "холодный" бутстрап проектов из сервера в Room
     */
    fun bootstrap() {
        viewModelScope.launch {
            val session = authRepo.currentSession()
            if (session != null && !session.isExpired) {
                // 1) сразу даём UI зелёный свет
                _state.value = State.Success(session)
                // 2) фоном подтягиваем проекты в Room
                launch { runCatching { projectsRepo.bootstrapFromRemote() } }
            } else {
                _state.value = State.Idle
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
     * После успеха фоном запускаем бутстрап проектов.
     */
    fun handleResult(result: YandexAuthResult) {
        viewModelScope.launch {
            val res = authRepo.handleAuthResult(result)
            _state.value = res.fold(
                onSuccess = { session ->
                    // отдадим UI Success
                    State.Success(session).also {
                        // и фоном подтянем проекты → Room (после чистой установки)
                        launch { runCatching { projectsRepo.bootstrapFromRemote() } }
                    }
                },
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
            authRepo.signOut()
            _state.value = State.Idle
        }
    }

    private fun mapThrowableToUi(t: Throwable): String {
        // authRepo в failure возвращает RuntimeException с коротким кодом:
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