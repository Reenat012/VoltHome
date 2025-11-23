package ru.mugalimov.volthome.data.remote.secure

import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.google.gson.Gson
import java.io.InputStream
import java.io.OutputStream

class JwtStoreSerializer(
    private val aead: Aead,
    private val gson: Gson = Gson()
) : Serializer<JwtStore> {

    override val defaultValue: JwtStore = JwtStore()

    override suspend fun readFrom(input: InputStream): JwtStore {
        val ciphertext = input.readBytes()
        if (ciphertext.isEmpty()) return defaultValue

        return try {
            val plaintext = aead.decrypt(ciphertext, /*associatedData=*/ null)
            val json = plaintext.decodeToString()
            gson.fromJson(json, JwtStore::class.java) ?: defaultValue
        } catch (t: Throwable) {
            // Любой сбой (битый файл, неправильный ключ, KeyStoreException и т.д.)
            // трактуем как "нет сессии", чтобы не падать.
            defaultValue
        }
    }

    override suspend fun writeTo(t: JwtStore, output: OutputStream) {
        val json = gson.toJson(t)
        val plaintext = json.encodeToByteArray()
        val ciphertext = aead.encrypt(plaintext, /*associatedData=*/ null)
        output.write(ciphertext)
    }
}