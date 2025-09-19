package ru.mugalimov.volthome.data.remote.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val session = runBlocking { sessionManager.load() }

        val withAuth = session?.accessToken?.let { jwt ->
            original.newBuilder()
                .header("Authorization", "Bearer $jwt")
                .build()
        } ?: original

        return chain.proceed(withAuth)
    }
}