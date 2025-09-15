package ru.mugalimov.volthome.data.remote.yandex

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YandexUserRemoteDataSource @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson
) {

    /**
     * Требуется scope "login:info". Если токен без нужного скоупа — вернётся 403.
     */
    suspend fun getUserInfo(accessToken: String): Result<YandexUserInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "https://login.yandex.ru/info".toHttpUrl()
                .newBuilder()
                .addQueryParameter("format", "json")
                .build()

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "OAuth $accessToken")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val code = resp.code
                    val body = resp.body?.string().orEmpty()
                    val err = when (code) {
                        401 -> "unauthorized"
                        403 -> "forbidden_scope" // нет login:info
                        else -> "http_$code"
                    }
                    return@withContext Result.failure(RuntimeException("$err: $body"))
                }

                val body = resp.body?.charStream() ?: return@withContext Result.failure(RuntimeException("empty_body"))
                val info = gson.fromJson(body, YandexUserInfo::class.java)
                Result.success(info)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}