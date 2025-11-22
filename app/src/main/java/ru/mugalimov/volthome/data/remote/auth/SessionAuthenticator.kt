package ru.mugalimov.volthome.data.remote.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionAuthenticator @Inject constructor(
    private val refreshGate: RefreshGate,
    private val sessionManager: SessionManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Не пытаться бесконечно
        if (response.priorResponse != null) return null

        val result = runBlocking { refreshGate.forceRefresh() }
        return when (result) {
            is RefreshGate.Result.Succeeded -> {
                val header = runBlocking { sessionManager.currentBearerOrNull() } ?: return null
                response.request.newBuilder()
                    .header("Authorization", header)
                    .build()
            }

            is RefreshGate.Result.Failed -> {
                if (result.kind == RefreshGate.FailureKind.UNAUTHORIZED) {
                    // Фатальное состояние по актуальному refresh — мягкий logout
                    runBlocking { sessionManager.clear() }
                    null
                } else {
                    // Сеть/unknown — не чистим сессию; пусть запрос упадет 401 и UI отреагирует.
                    null
                }
            }

            is RefreshGate.Result.Idle -> {
                // Idle после 401 маловероятно; повторять нечем
                null
            }
        }
    }
}