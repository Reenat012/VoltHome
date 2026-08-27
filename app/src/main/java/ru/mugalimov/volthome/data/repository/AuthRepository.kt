package ru.mugalimov.volthome.data.repository

import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthResult
import ru.mugalimov.volthome.data.local.auth.LocalAuthSession

interface AuthRepository {

    /**
     * Создаёт параметры авторизации для запуска через ActivityResultLauncher.
     * Централизовано, без создания в UI.
     */
    fun loginOptions(): YandexAuthLoginOptions

    /** Обрабатывает результат авторизации. Возвращает актуальную сессию или ошибку. */
    suspend fun handleAuthResult(result: YandexAuthResult): Result<LocalAuthSession>

    /** Создаёт локальную гостевую сессию без обращения к сети. */
    suspend fun continueAsGuest(): LocalAuthSession

    /** Возвращает сохранённую сессию (или null). */
    suspend fun currentSession(): LocalAuthSession?

    /** Полный выход. */
    suspend fun signOut()

    /** Удобный хелпер для UI: true, если сохранена локальная сессия. */
    suspend fun isLoggedIn(): Boolean = currentSession() != null
}
