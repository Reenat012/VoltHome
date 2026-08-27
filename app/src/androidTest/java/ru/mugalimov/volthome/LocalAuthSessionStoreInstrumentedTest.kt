package ru.mugalimov.volthome

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import ru.mugalimov.volthome.data.local.auth.LocalAuthProvider
import ru.mugalimov.volthome.data.local.auth.LocalAuthSession
import ru.mugalimov.volthome.data.local.auth.LocalAuthSessionStore
import ru.mugalimov.volthome.data.local.prefs.EncryptedPrefsProvider

@RunWith(AndroidJUnit4::class)
class LocalAuthSessionStoreInstrumentedTest {
    @Test
    fun legacySessionMigratesAndSurvivesStoreRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteSharedPreferences("auth_prefs")
        context.deleteSharedPreferences("auth_prefs_fallback")
        File(
            context.filesDir,
            "datastore/local_auth_session_v2.preferences_pb"
        ).delete()

        val encryptedProvider = EncryptedPrefsProvider(context)
        val legacyPrefs = encryptedProvider.get()
        legacyPrefs.edit()
            .putString("local_auth_provider", "YANDEX")
            .putString("local_auth_uid", "legacy-local-user")
            .putString("local_auth_display_name", "Сохранённый пользователь")
            .commit()

        val firstStore = LocalAuthSessionStore(context, encryptedProvider)
        val migrated = firstStore.load()
        requireNotNull(migrated)
        assertEquals(LocalAuthProvider.YANDEX, migrated.provider)
        assertEquals("legacy-local-user", migrated.uid)

        firstStore.save(
            LocalAuthSession(
                provider = LocalAuthProvider.YANDEX,
                uid = migrated.uid,
                displayName = "Обновлённый профиль"
            )
        )

        val recreatedStore = LocalAuthSessionStore(context, encryptedProvider)
        assertEquals("Обновлённый профиль", recreatedStore.load()?.displayName)

        recreatedStore.clear()
        assertNull(recreatedStore.load())
    }
}
