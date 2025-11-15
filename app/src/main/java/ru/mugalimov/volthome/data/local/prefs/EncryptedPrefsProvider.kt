package ru.mugalimov.volthome.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ленивая и потокобезопасная инициализация EncryptedSharedPreferences на IO.
 * Устраняет StrictMode DiskReadViolation на главном потоке.
 */
@Singleton
class EncryptedPrefsProvider @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    @Volatile private var cached: SharedPreferences? = null
    private val mutex = Mutex()

    suspend fun get(): SharedPreferences {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            withContext(Dispatchers.IO) {
                val masterKey = MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    ctx,
                    "auth_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { cached = it }
            }
        }
    }
}