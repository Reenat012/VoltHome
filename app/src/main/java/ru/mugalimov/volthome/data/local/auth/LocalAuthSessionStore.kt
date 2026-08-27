package ru.mugalimov.volthome.data.local.auth

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.prefs.EncryptedPrefsProvider

private val Context.localAuthSessionDataStore by preferencesDataStore(
    name = "local_auth_session_v2"
)

/**
 * Долговечная локальная сессия приложения.
 *
 * Здесь нет OAuth-token или другого секрета: сессия отвечает только за доступ
 * к локальному приложению и потому не зависит от доступности AndroidKeyStore.
 */
@Singleton
class LocalAuthSessionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val encryptedPrefsProvider: EncryptedPrefsProvider
) {
    private companion object {
        const val SCHEMA_VERSION = 2

        val KEY_SCHEMA_VERSION = intPreferencesKey("schema_version")
        val KEY_MIGRATION_DONE = booleanPreferencesKey("legacy_migration_done")
        val KEY_PROVIDER = stringPreferencesKey("provider")
        val KEY_UID = stringPreferencesKey("uid")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_AVATAR_URL = stringPreferencesKey("avatar_url")

        const val LEGACY_PROVIDER = "local_auth_provider"
        const val LEGACY_UID = "local_auth_uid"
        const val LEGACY_DISPLAY_NAME = "local_auth_display_name"
        const val LEGACY_EMAIL = "local_auth_email"
        const val LEGACY_AVATAR_URL = "local_auth_avatar_url"
        const val LEGACY_SERVER_UID = "session_uid"
        const val LEGACY_YANDEX_TOKEN = "ya_access_token"
    }

    private val dataStore = context.localAuthSessionDataStore
    private val migrationMutex = Mutex()

    suspend fun load(): LocalAuthSession? = withContext(Dispatchers.IO) {
        ensureLegacyMigration()
        dataStore.data.first().toSession()
    }

    suspend fun createGuest(): LocalAuthSession {
        val existing = load()
        if (existing?.provider == LocalAuthProvider.GUEST) return existing

        val session = LocalAuthSession(
            provider = LocalAuthProvider.GUEST,
            uid = "guest-${UUID.randomUUID()}",
            displayName = "Гость"
        )
        save(session)
        return session
    }

    suspend fun save(session: LocalAuthSession) = withContext(Dispatchers.IO) {
        dataStore.edit { prefs ->
            prefs[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
            prefs[KEY_MIGRATION_DONE] = true
            prefs[KEY_PROVIDER] = session.provider.name
            prefs[KEY_UID] = session.uid
            prefs[KEY_DISPLAY_NAME] = session.displayName
            putOptional(prefs, KEY_EMAIL, session.email)
            putOptional(prefs, KEY_AVATAR_URL, session.avatarUrl)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
            // Не даём старым ключам воскресить сессию после явного logout.
            prefs[KEY_MIGRATION_DONE] = true
        }
    }

    private suspend fun ensureLegacyMigration() {
        val current = dataStore.data.first()
        if (current[KEY_MIGRATION_DONE] == true) return

        migrationMutex.withLock {
            val checkedAgain = dataStore.data.first()
            if (checkedAgain[KEY_MIGRATION_DONE] == true) return

            val migrated = readLegacySnapshot()?.let(AuthSessionMigration::restore)
            dataStore.edit { prefs ->
                prefs[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
                prefs[KEY_MIGRATION_DONE] = true
                if (migrated != null) {
                    prefs[KEY_PROVIDER] = migrated.provider.name
                    prefs[KEY_UID] = migrated.uid
                    prefs[KEY_DISPLAY_NAME] = migrated.displayName
                    putOptional(prefs, KEY_EMAIL, migrated.email)
                    putOptional(prefs, KEY_AVATAR_URL, migrated.avatarUrl)
                }
            }
        }
    }

    private suspend fun readLegacySnapshot(): LegacyAuthSnapshot? {
        val encrypted = runCatching { encryptedPrefsProvider.get() }
        if (encrypted.isSuccess) {
            return encrypted.getOrThrow().toLegacySnapshot()
        }

        // Fallback читается только для миграции старых версий. Если он пуст,
        // не завершаем миграцию: зашифрованная старая сессия может стать
        // доступной после перезапуска процесса.
        val fallbackSnapshot = encryptedPrefsProvider.fallbackPrefs().toLegacySnapshot()
        if (fallbackSnapshot != null) return fallbackSnapshot
        throw encrypted.exceptionOrNull()
            ?: IllegalStateException("Legacy credential storage is unavailable")
    }

    private fun android.content.SharedPreferences.toLegacySnapshot(): LegacyAuthSnapshot? {
        val snapshot = LegacyAuthSnapshot(
            provider = getString(LEGACY_PROVIDER, null),
            localUid = getString(LEGACY_UID, null),
            displayName = getString(LEGACY_DISPLAY_NAME, null),
            email = getString(LEGACY_EMAIL, null),
            avatarUrl = getString(LEGACY_AVATAR_URL, null),
            serverUid = getString(LEGACY_SERVER_UID, null),
            yandexAccessToken = getString(LEGACY_YANDEX_TOKEN, null)
        )

        return snapshot.takeIf {
            it.provider != null ||
                it.localUid != null ||
                it.serverUid != null ||
                it.yandexAccessToken != null
        }
    }

    private fun Preferences.toSession(): LocalAuthSession? {
        val provider = this[KEY_PROVIDER]
            ?.let { runCatching { LocalAuthProvider.valueOf(it) }.getOrNull() }
            ?: return null
        val uid = this[KEY_UID]?.takeIf(String::isNotBlank) ?: return null
        val defaultName = if (provider == LocalAuthProvider.GUEST) {
            "Гость"
        } else {
            "Пользователь Яндекс ID"
        }

        return LocalAuthSession(
            provider = provider,
            uid = uid,
            displayName = this[KEY_DISPLAY_NAME].orEmpty().ifBlank { defaultName },
            email = this[KEY_EMAIL],
            avatarUrl = this[KEY_AVATAR_URL]
        )
    }

    private fun putOptional(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<String>,
        value: String?
    ) {
        if (value.isNullOrBlank()) prefs.remove(key) else prefs[key] = value
    }
}
