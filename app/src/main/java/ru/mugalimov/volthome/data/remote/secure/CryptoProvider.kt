package ru.mugalimov.volthome.data.remote.secure

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager

object CryptoProvider {
    private const val MASTER_KEY_URI = "android-keystore://volthome_master_key"
    private const val KEYSET_NAME   = "volthome_jwt_keyset"
    private const val PREF_FILE     = "volthome_jwt_keyset_prefs"

    fun aead(context: Context): Aead {
        AeadConfig.register()
        val handle: KeysetHandle = AndroidKeysetManager.Builder()
            .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
            .withSharedPref(context, KEYSET_NAME, PREF_FILE)
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        return handle.getPrimitive(Aead::class.java)
    }
}