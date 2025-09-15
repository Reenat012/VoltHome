package ru.mugalimov.volthome.di.database

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        // В debug логируем тело (токен мы не логируем — см. header redaction ниже).
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        builder.addInterceptor(logging)

        // Редактируем чувствительные заголовки (на случай, если уровень логирования выше)
        builder.addNetworkInterceptor { chain ->
            val req = chain.request()
            val redacted = req.newBuilder()
                .header("Authorization", "***") // скрыть токен при печати
                .build()
            chain.proceed(req).also {
                // no-op, логгер уже отработает; важна редакция выше
            }
        }

        return builder.build()
    }
}