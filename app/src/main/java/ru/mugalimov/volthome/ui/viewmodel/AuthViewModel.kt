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
import ru.mugalimov.volthome.data.repository.impl.ProjectsRepositoryImpl
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val projectsRepo: ProjectsRepository,
    // нам нужен ensureActiveOrCreateLocalDraft(); он в impl — инжектим через интерфейс сверху,
    // а вызвать будем через safe cast, если реализация наша.
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
                // 1) сразу даём UI зелёный свет
                _state.value = State.Success(session)

                // 2) гарантируем активный проект (черновик при необходимости)
                launch { runCatching { projectsRepo.ensureActiveDraft() } }

                // 3) фоном подтягиваем проекты в Room (будет 1 раз благодаря защите в репозитории)
                launch { runCatching { projectsRepo.bootstrapFromRemote() } }
            } else {
                _state.value = State.Idle
            }
        }
    }

    fun startLogin() { _state.value = State.Loading }

    fun handleResult(result: YandexAuthResult) {
        viewModelScope.launch {
            val res = authRepo.handleAuthResult(result)
            _state.value = res.fold(
                onSuccess = { session ->
                    // Успех авторизации
                    projectsRepo.ensureActiveDraft()

                    // чФоновый бутстрап (идемпотентно)
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