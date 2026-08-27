package ru.mugalimov.volthome.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Ленивая и потокобезопасная инициализация EncryptedSharedPreferences на IO.
 *
 * ВАЖНО: ошибки AndroidKeyStore не должны приводить к удалению credential.
 * Провайдер оставляет зашифрованные данные нетронутыми и позволяет повторить
 * инициализацию после перезапуска процесса.
 */
@Singleton
class EncryptedPrefsProvider @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private companion object {
        private const val TAG = "EncryptedPrefs"
        private const val AUTH_PREFS_NAME = "auth_prefs"
        private const val FALLBACK_PREFS_NAME = "auth_prefs_fallback"
    }
    @Volatile
    private var cached: SharedPreferences? = null
    private val mutex = Mutex()

    suspend fun get(): SharedPreferences {
        cached?.let { return it }

        return mutex.withLock {
            cached?.let { return it }

            val prefs = withContext(Dispatchers.IO) {
                initEncryptedPrefs()
            }
            cached = prefs
            prefs
        }
    }

    private fun initEncryptedPrefs(): SharedPreferences {
        return try {
            createEncryptedPrefs().also(::migrateFallbackIfPresent)
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Encrypted preferences are temporarily unavailable; data was not deleted", e)
            throw EncryptedPrefsUnavailableException(e)
        } catch (e: IOException) {
            Log.e(TAG, "Encrypted preferences are temporarily unavailable; data was not deleted", e)
            throw EncryptedPrefsUnavailableException(e)
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            ctx,
            AUTH_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Старые версии могли временно записать auth-данные в plain fallback.
     * После восстановления KeyStore переносим их в encrypted prefs и очищаем
     * fallback только после успешного commit.
     */
    private fun migrateFallbackIfPresent(encrypted: SharedPreferences) {
        val fallback = fallbackPrefs()
        val values = fallback.all
        if (values.isEmpty()) return

        val editor = encrypted.edit()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }
        if (editor.commit()) {
            fallback.edit().clear().commit()
            Log.i(TAG, "Migrated legacy fallback preferences into encrypted storage")
        } else {
            Log.w(TAG, "Fallback migration was not committed; source data was preserved")
        }
    }

    internal fun fallbackPrefs(): SharedPreferences =
        ctx.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
}

class EncryptedPrefsUnavailableException(cause: Throwable) :
    IllegalStateException("Encrypted credential storage is unavailable", cause)
