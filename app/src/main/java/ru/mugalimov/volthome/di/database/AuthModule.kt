package ru.mugalimov.volthome.di.database

import android.content.Context
import android.util.Log
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthSdk
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.data.local.prefs.EncryptedPrefsProvider
import java.lang.reflect.Method
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    private const val TAG = "AuthModule"

    @Provides
    @Singleton
    fun provideYandexAuthOptions(@ApplicationContext ctx: Context): YandexAuthOptions {
        val clientId = BuildConfig.YANDEX_CLIENT_ID.trim()

        // Основной путь: пробуем создать options с явным clientId (если SDK это поддерживает).
        // Фолбэк: стандартный конструктор (SDK может читать clientId из manifestPlaceholders).
        val optionsFromBuilder = runCatching {
            createOptionsWithClientIdViaReflection(ctx, clientId)
        }.getOrNull()

        if (optionsFromBuilder != null) return optionsFromBuilder

        if (clientId.isBlank()) {
            Log.e(TAG, "YANDEX_CLIENT_ID is blank. Falling back to YandexAuthOptions(ctx). OAuth must be gated in VM.")
        }

        return YandexAuthOptions(ctx)
    }

    @Provides
    @Singleton
    fun provideYandexAuthSdk(
        options: YandexAuthOptions
    ): YandexAuthSdk = YandexAuthSdk.create(options)

    /**
     * ВАЖНО: Не создаём EncryptedSharedPreferences синхронно.
     * Отдаём ленивый провайдер, который инициализируется на IO.
     */
    @Provides
    @Singleton
    fun provideEncryptedPrefsProvider(
        @ApplicationContext ctx: Context
    ): EncryptedPrefsProvider = EncryptedPrefsProvider(ctx)

    /**
     * Пытаемся собрать YandexAuthOptions с явным clientId без жёсткой привязки к конкретному API SDK.
     *
     * Ожидаемые варианты (в зависимости от версии SDK):
     * - com.yandex.authsdk.YandexAuthOptions$Builder(Context).setClientId(String).build()
     * - com.yandex.authsdk.YandexAuthOptions$Builder(Context).clientId(String).build()
     *
     * Если ни один не найден — вернём null (пусть будет фолбэк на YandexAuthOptions(ctx)).
     */
    private fun createOptionsWithClientIdViaReflection(
        ctx: Context,
        clientId: String
    ): YandexAuthOptions? {
        if (clientId.isBlank()) return null

        val builderClass = runCatching {
            Class.forName("com.yandex.authsdk.YandexAuthOptions\$Builder")
        }.getOrNull() ?: return null

        val builder = runCatching {
            builderClass.getConstructor(Context::class.java).newInstance(ctx)
        }.getOrNull() ?: return null

        val setClientId: Method? =
            builderClass.methods.firstOrNull { m ->
                (m.name == "setClientId" || m.name == "clientId") &&
                        m.parameterTypes.size == 1 &&
                        m.parameterTypes[0] == String::class.java
            }

        if (setClientId == null) return null

        runCatching { setClientId.invoke(builder, clientId) }.getOrNull() ?: return null

        val buildMethod = builderClass.methods.firstOrNull { it.name == "build" && it.parameterTypes.isEmpty() }
            ?: return null

        val built = runCatching { buildMethod.invoke(builder) }.getOrNull() ?: return null
        return built as? YandexAuthOptions
    }
}