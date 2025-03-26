package ru.mugalimov.volthome.di.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.domain.use_case.CalcSumPowerDevices

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideCalculateLoadUseCase(
        repository: DeviceRepository
    ): CalcSumPowerDevices {
        return CalcSumPowerDevices(repository)
    }
}