package ru.mugalimov.volthome.data.remote.yandex

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import ru.mugalimov.volthome.data.local.prefs.EncryptedPrefsProvider

@Singleton
class YandexTokenStore @Inject constructor(
    private val prefsProvider: EncryptedPrefsProvider
) {
    private companion object { const val KEY = "ya_access_token" }

    private suspend fun prefs(): SharedPreferences = prefsProvider.get()

    // ---- Рекомендуемые suspend-методы ----
    suspend fun save(token: String?) = withContext(Dispatchers.IO) {
        val p = prefs().edit()
        if (token.isNullOrBlank()) p.remove(KEY) else p.putString(KEY, token)
        p.commit() // синхронно, чтобы сразу читалось актуальное
    }

    suspend fun get(): String? = withContext(Dispatchers.IO) { prefs().getString(KEY, null) }

    suspend fun clear() = withContext(Dispatchers.IO) { prefs().edit().remove(KEY).commit() }

    // ---- Временные blocking-обёртки для старых call-sites (не вызывай с UI!) ----
    @Deprecated("Используй suspend save(token)")
    fun saveBlocking(token: String?) = runBlocking(Dispatchers.IO) { save(token) }

    @Deprecated("Используй suspend get()")
    fun getBlocking(): String? = runBlocking(Dispatchers.IO) { get() }

    @Deprecated("Используй suspend clear()")
    fun clearBlocking() = runBlocking(Dispatchers.IO) { clear() }
}