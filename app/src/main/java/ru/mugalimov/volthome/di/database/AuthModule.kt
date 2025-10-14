package ru.mugalimov.volthome.di.database


import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthSdk
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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

    @Provides
    @Singleton
    fun provideEncryptedPrefs(@ApplicationContext ctx: Context) =
        EncryptedSharedPreferences.create(
            "auth_prefs",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            ctx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
}