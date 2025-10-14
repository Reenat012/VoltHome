package ru.mugalimov.volthome.data.remote.yandex

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YandexTokenStore @Inject constructor(
    private val prefs: SharedPreferences // EncryptedSharedPreferences из твоего AuthModule
) {
    private val KEY = "ya_access_token"

    fun save(token: String?) {
        if (token.isNullOrBlank()) {
            prefs.edit().remove(KEY).apply()
        } else {
            prefs.edit().putString(KEY, token).apply()
        }
    }

    fun get(): String? = prefs.getString(KEY, null)

    fun clear() { prefs.edit().remove(KEY).apply() }
}