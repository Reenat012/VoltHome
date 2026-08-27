package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.mugalimov.volthome.data.local.auth.AuthSessionMigration
import ru.mugalimov.volthome.data.local.auth.LegacyAuthSnapshot
import ru.mugalimov.volthome.data.local.auth.LocalAuthProvider

class AuthSessionMigrationTest {
    @Test
    fun `current yandex session has priority over legacy server values`() {
        val restored = AuthSessionMigration.restore(
            LegacyAuthSnapshot(
                provider = "YANDEX",
                localUid = "current-uid",
                displayName = "Ринат",
                email = "user@example.test",
                serverUid = "legacy-uid",
                yandexAccessToken = "legacy-token"
            )
        )

        requireNotNull(restored)
        assertEquals(LocalAuthProvider.YANDEX, restored.provider)
        assertEquals("current-uid", restored.uid)
        assertEquals("Ринат", restored.displayName)
        assertEquals("user@example.test", restored.email)
    }

    @Test
    fun `legacy server uid restores yandex session`() {
        val restored = AuthSessionMigration.restore(
            LegacyAuthSnapshot(serverUid = "server-user-42")
        )

        requireNotNull(restored)
        assertEquals(LocalAuthProvider.YANDEX, restored.provider)
        assertEquals("server-user-42", restored.uid)
    }

    @Test
    fun `legacy token creates stable non-secret uid`() {
        val first = AuthSessionMigration.restore(
            LegacyAuthSnapshot(yandexAccessToken = "same-token")
        )
        val second = AuthSessionMigration.restore(
            LegacyAuthSnapshot(yandexAccessToken = "same-token")
        )
        val other = AuthSessionMigration.restore(
            LegacyAuthSnapshot(yandexAccessToken = "other-token")
        )

        requireNotNull(first)
        requireNotNull(second)
        requireNotNull(other)
        assertEquals(first.uid, second.uid)
        assertNotEquals(first.uid, other.uid)
        assertNotEquals("same-token", first.uid)
    }

    @Test
    fun `legacy guest session requires a new explicit user choice`() {
        val restored = AuthSessionMigration.restore(
            LegacyAuthSnapshot(
                provider = "GUEST",
                localUid = "guest-existing"
            )
        )

        assertNull(restored)
    }

    @Test
    fun `empty or malformed snapshot does not authenticate user`() {
        assertNull(AuthSessionMigration.restore(LegacyAuthSnapshot()))
        assertNull(
            AuthSessionMigration.restore(
                LegacyAuthSnapshot(provider = "UNKNOWN", localUid = "uid")
            )
        )
    }
}
