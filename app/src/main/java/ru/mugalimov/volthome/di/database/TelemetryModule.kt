package ru.mugalimov.volthome.di.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.mugalimov.volthome.domain.telemetry.CreateDeviceOpBus

@Module
@InstallIn(SingletonComponent::class)
object TelemetryModule {

    /**
     * ✅ Один CreateDeviceOpBus на всё приложение.
     * Это правильно: корреляция должна быть сквозной.
     */
    @Provides
    @Singleton
    fun provideCreateDeviceOpBus(): CreateDeviceOpBus = CreateDeviceOpBus()
}