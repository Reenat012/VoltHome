package ru.mugalimov.volthome.data.repository

import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthResult
import ru.mugalimov.volthome.data.remote.auth.AuthSession

interface AuthRepository {

    /**
     * Создаёт параметры авторизации для запуска через ActivityResultLauncher.
     * Централизовано, без создания в UI.
     */
    fun loginOptions(): YandexAuthLoginOptions

    /** Обрабатывает результат авторизации. Возвращает актуальную сессию или ошибку. */
    suspend fun handleAuthResult(result: YandexAuthResult): Result<AuthSession>

    /** Возвращает сохранённую сессию (или null). */
    suspend fun currentSession(): AuthSession?

    /** Полный выход. */
    suspend fun signOut()

    /** Удобный хелпер для UI: true, если есть неистёкшая сессия. */
    suspend fun isLoggedIn(): Boolean = currentSession()?.isExpired == false
}