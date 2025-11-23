package ru.mugalimov.volthome.data.remote.secure

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

object CryptoProvider {
    private const val MASTER_KEY_URI = "android-keystore://volthome_master_key"
    private const val KEYSET_NAME   = "volthome_jwt_keyset"
    private const val PREF_FILE     = "volthome_jwt_keyset_prefs"
    private const val MASTER_KEY_ALIAS = "volthome_master_key"

    fun aead(context: Context): Aead {
        AeadConfig.register()

        // 1-й заход
        try {
            return createAead(context)
        } catch (e: GeneralSecurityException) {
            // битый/инвалидированный ключ в Keystore или ключсет
            resetKeystoreAndKeyset(context)
        } catch (e: IOException) {
            // повреждённый keyset в SharedPreferences
            resetKeystoreAndKeyset(context)
        }

        // 2-й заход после сброса
        return try {
            createAead(context)
        } catch (t: Throwable) {
            // Здесь уже совсем всё плохо — логически лучше упасть, чем тихо хранить токены в plain text.
            throw IllegalStateException("Failed to init Tink AEAD after keystore reset", t)
        }
    }

    private fun createAead(context: Context): Aead {
        val handle: KeysetHandle = AndroidKeysetManager.Builder()
            .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
            .withSharedPref(context, KEYSET_NAME, PREF_FILE)
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle

        return handle.getPrimitive(Aead::class.java)
    }

    private fun resetKeystoreAndKeyset(context: Context) {
        // 1. Чистим SharedPreferences с keyset-ом
        runCatching {
            context.getSharedPreferences(PREF_FILE, MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }

        // 2. Удаляем мастер-ключ из AndroidKeyStore
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(MASTER_KEY_ALIAS)) {
                ks.deleteEntry(MASTER_KEY_ALIAS)
            }
        }
    }
}