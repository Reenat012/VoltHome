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
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

/**
 * Ленивая и потокобезопасная инициализация EncryptedSharedPreferences на IO.
 * Теперь с восстановлением при битом ключе/файле.
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
            val prefs = withContext(Dispatchers.IO) {
                initEncryptedPrefsWithRecovery()
            }
            cached = prefs
            prefs
        }
    }

    private fun initEncryptedPrefsWithRecovery(): SharedPreferences {
        try {
            return createEncryptedPrefs()
        } catch (e: GeneralSecurityException) {
            resetEncryptedPrefs()
        } catch (e: IOException) {
            resetEncryptedPrefs()
        }

        // повторная инициализация после сброса
        return createEncryptedPrefs()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            ctx,
            "auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun resetEncryptedPrefs() {
        // 1. Удаляем файл encrypted prefs
        runCatching {
            ctx.deleteSharedPreferences("auth_prefs")
        }

        // 2. Удаляем master key alias Jetpack Security из Android Keystore
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = MasterKey.DEFAULT_MASTER_KEY_ALIAS
            if (ks.containsAlias(alias)) {
                ks.deleteEntry(alias)
            }
        }
    }
}