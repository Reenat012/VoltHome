package ru.mugalimov.volthome.di.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.use_case.CalcLoads
import ru.mugalimov.volthome.domain.use_case.GroupCalculator

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideCalculateLoadUseCase(
        repository: DeviceRepository
    ): CalcLoads {
        return CalcLoads(repository)
    }

    @Provides
    fun provideCalculateGroupUseCase(
        repository: RoomRepository
    ): GroupCalculator {
        return GroupCalculator(repository)
    }
}