package ru.mugalimov.volthome.data.remote.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
    private val refreshGate: RefreshGate
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // Если где-то уже идет refresh — дождемся, чтобы приклеить актуальный access
        runBlocking {
            refreshGate.awaitIfRunning()
        }

        val header = runBlocking { sessionManager.currentBearerOrNull() }
        val req: Request = if (header != null) {
            chain.request().newBuilder().header("Authorization", header).build()
        } else {
            chain.request()
        }

        return chain.proceed(req)
    }
}