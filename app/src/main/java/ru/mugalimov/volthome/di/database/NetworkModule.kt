package ru.mugalimov.volthome.di.database

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.data.remote.api.AuthApi
import ru.mugalimov.volthome.data.remote.api.ProfileApi
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.data.remote.auth.AuthInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides @Singleton @Named("baseUrl")
    fun provideBaseUrl(): String = BuildConfig.API_BASE_URL

    @Provides @Singleton @Named("logging")
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor { msg ->
            // пишем в лог только в debug
            if (BuildConfig.DEBUG) android.util.Log.d("HTTP", msg)
        }.apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

    // --- AUTHLESS --- //
    @Provides @Singleton @Named("authless")
    fun provideAuthlessOkHttp(
        @Named("logging") logging: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    @Provides @Singleton @Named("authless")
    fun provideAuthlessRetrofit(
        @Named("baseUrl") baseUrl: String,
        @Named("authless") client: OkHttpClient,
        gson: Gson
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    /** Api для exchange/refresh — по authless-клиенту */
    @Provides @Singleton @Named("refreshApi")
    fun provideRefreshAuthApi(@Named("authless") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    // --- AUTHED --- //
    // ВАЖНО: НЕ делаем явный провайдер AuthInterceptor, Hilt сконструирует его сам по @Inject-конструктору
    @Provides @Singleton @Named("authed")
    fun provideAuthedOkHttp(
        @Named("logging") logging: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .addInterceptor(authInterceptor)
        .build()

    @Provides @Singleton
    fun provideRetrofit(
        @Named("baseUrl") baseUrl: String,
        @Named("authed") client: OkHttpClient,
        gson: Gson
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    // Основные API через authed
    @Provides @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApi = retrofit.create(ProfileApi::class.java)

    @Provides @Singleton
    fun provideProjectsApi(retrofit: Retrofit): ProjectsApi = retrofit.create(ProjectsApi::class.java)

    // 🔁 Вернём «дефолтный» OkHttpClient для мест, где инжектится без @Named
    @Provides @Singleton
    fun provideDefaultOkHttp(@Named("authless") client: OkHttpClient): OkHttpClient = client
}