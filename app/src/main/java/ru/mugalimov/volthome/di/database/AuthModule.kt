package ru.mugalimov.volthome.di.database

import android.content.Context
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthSdk
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.mugalimov.volthome.data.local.prefs.EncryptedPrefsProvider

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideYandexAuthOptions(@ApplicationContext ctx: Context): YandexAuthOptions =
        YandexAuthOptions(ctx)

    @Provides
    @Singleton
    fun provideYandexAuthSdk(
        @ApplicationContext ctx: Context,
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
}