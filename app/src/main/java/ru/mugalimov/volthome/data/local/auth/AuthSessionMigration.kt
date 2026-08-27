package ru.mugalimov.volthome.data.local.auth

import java.security.MessageDigest

internal data class LegacyAuthSnapshot(
    val provider: String? = null,
    val localUid: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val serverUid: String? = null,
    val yandexAccessToken: String? = null
)

/**
 * Чистая часть миграции. Вынесена отдельно, чтобы переход между версиями
 * можно было проверить обычными unit-тестами без Android Context.
 */
internal object AuthSessionMigration {
    fun restore(snapshot: LegacyAuthSnapshot): LocalAuthSession? {
        val currentProvider = snapshot.provider
            ?.let { runCatching { LocalAuthProvider.valueOf(it) }.getOrNull() }
        val currentUid = snapshot.localUid?.takeIf(String::isNotBlank)

        // Гостевой режим не содержит ценной учётной записи и должен начинаться
        // только после явного выбора пользователя. Старые версии сохраняли гостя
        // как обычную сессию, из-за чего после обновления или восстановления данных
        // экран выбора входа мог быть пропущен. Новые гостевые сессии продолжают
        // жить в LocalAuthSessionStore и восстанавливаются между обычными запусками;
        // здесь отсекается только одноразовая миграция legacy-хранилища.
        if (currentProvider == LocalAuthProvider.GUEST) return null

        if (currentProvider != null && currentUid != null) {
            return LocalAuthSession(
                provider = currentProvider,
                uid = currentUid,
                displayName = snapshot.displayName.orEmpty().ifBlank {
                    if (currentProvider == LocalAuthProvider.GUEST) "Гость"
                    else "Пользователь Яндекс ID"
                },
                email = snapshot.email?.takeIf(String::isNotBlank),
                avatarUrl = snapshot.avatarUrl?.takeIf(String::isNotBlank)
            )
        }

        val legacyUid = snapshot.serverUid?.takeIf(String::isNotBlank)
            ?: snapshot.yandexAccessToken
                ?.takeIf(String::isNotBlank)
                ?.let(::stableUidFromToken)
            ?: return null

        return LocalAuthSession(
            provider = LocalAuthProvider.YANDEX,
            uid = legacyUid,
            displayName = "Пользователь Яндекс ID"
        )
    }

    private fun stableUidFromToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString(separator = "") { "%02x".format(it) }
        return "yandex-$digest"
    }
}
