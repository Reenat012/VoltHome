package ru.mugalimov.volthome.data.remote.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import ru.mugalimov.volthome.data.remote.api.AuthApi
import ru.mugalimov.volthome.data.remote.api.RefreshRequest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SessionAuthenticator @Inject constructor(
    @Named("refreshApi") private val authApi: AuthApi,
    private val sessionManager: SessionManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null

        val current = runBlocking { sessionManager.load() } ?: return null
        val refreshId = current.refreshId

        return try {
            val refreshed = runBlocking {
                authApi.refresh(RefreshRequest(refreshId = refreshId))
            }
            runBlocking {
                sessionManager.save(
                    sessionJwt = refreshed.sessionJwt,
                    expiresAtEpochSeconds = refreshed.expiresAtEpochSeconds,
                    refreshId = refreshed.refreshId
                )
            }
            response.request.newBuilder()
                .header("Authorization", "Bearer ${refreshed.sessionJwt}")
                .build()
        } catch (_: Throwable) {
            runBlocking { sessionManager.clear() }
            null
        }
    }
}