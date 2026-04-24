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
import java.security.KeyStore

/**
 * Ленивая и потокобезопасная инициализация EncryptedSharedPreferences на IO.
 * С авто-восстановлением при битом ключе / keyset'е.
 */
@Singleton
class EncryptedPrefsProvider @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private companion object {
        private const val TAG = "EncryptedPrefs"
        private const val AUTH_PREFS_NAME = "auth_prefs"
        private const val FALLBACK_PREFS_NAME = "auth_prefs_fallback"
        private const val TINK_PREFS_NAME = "__androidx_security_crypto_encrypted_prefs__"
    }
    @Volatile
    private var cached: SharedPreferences? = null
    private val mutex = Mutex()

    suspend fun get(): SharedPreferences {
        cached?.let { return it }

        return mutex.withLock {
            cached?.let { return it }

            val prefs = withContext(Dispatchers.IO) {
                initEncryptedPrefsWithRecovery()
            }
            cached = prefs
            prefs
        }
    }

    /**
     * Делаем до двух попыток:
     *  1. Обычная инициализация.
     *  2. Если словили GeneralSecurityException / IOException — сбрасываем всё
     *     и пробуем ещё раз.
     */
    private fun initEncryptedPrefsWithRecovery(): SharedPreferences {
        var lastError: Throwable? = null

        repeat(2) { attempt ->
            try {
                if (attempt > 0) {
                    Log.w(TAG, "retry init after reset")
                }
                return createEncryptedPrefs()
            } catch (e: GeneralSecurityException) {
                Log.w("EncryptedPrefs", "init failed with security error, resetting", e)
                lastError = e
                resetEncryptedPrefs()
            } catch (e: IOException) {
                Log.w("EncryptedPrefs", "init failed with IO error, resetting", e)
                lastError = e
                resetEncryptedPrefs()
            }
        }

        // Если после сброса всё равно не взлетело — это уже системная беда,
        // даём понятный крэш с оригинальной причиной.
        Log.e(
            "EncryptedPrefs",
            "EncryptedSharedPreferences failed after reset. Falling back to plain private SharedPreferences.",
            lastError
        )

        return createFallbackPrefs()
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

    private fun createFallbackPrefs(): SharedPreferences {
        return ctx.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Полный сброс:
     *  1. Удаляем наш encrypted prefs ("auth_prefs").
     *  2. Удаляем внутренний Tink-keyset, который использует EncryptedSharedPreferences.
     *  3. Сносим мастер-ключ в AndroidKeyStore.
     */
    private fun resetEncryptedPrefs() {
        // 1. Наш файл encrypted prefs
        runCatching {
            Log.w("EncryptedPrefs", "deleteSharedPreferences(\"auth_prefs\")")
            ctx.deleteSharedPreferences(AUTH_PREFS_NAME)
            ctx.deleteSharedPreferences(FALLBACK_PREFS_NAME)
        }

        // 2. Внутренний keyset EncryptedSharedPreferences (tink)
        //   EncryptedSharedPreferences хранит keyset в отдельном SharedPreferences-файле,
        //   который по умолчанию называется примерно так:
        //   "__androidx_security_crypto_encrypted_prefs__"
        runCatching {
            Log.w("EncryptedPrefs", "deleteSharedPreferences(\"__androidx_security_crypto_encrypted_prefs__\")")
            ctx.deleteSharedPreferences(TINK_PREFS_NAME)
        }

        // 3. Удаляем master key alias из AndroidKeyStore
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = MasterKey.DEFAULT_MASTER_KEY_ALIAS
            if (ks.containsAlias(alias)) {
                Log.w("EncryptedPrefs", "deleteEntry($alias) from AndroidKeyStore")
                ks.deleteEntry(alias)
            }
        }
    }
}