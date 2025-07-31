package ru.mugalimov.volthome.di.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.use_case.CalcLoads
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideCalculateLoadUseCase(
        deviceRepository: DeviceRepository,
        roomRepository: RoomRepository

    ): CalcLoads {
        return CalcLoads(deviceRepository, roomRepository)
    }

    @Provides
    fun provideGroupCalculatorFactory(
        roomRepo: RoomRepository,
        explicationRepo: ExplicationRepository
    ): GroupCalculatorFactory {
        return GroupCalculatorFactory(roomRepo, explicationRepo)
    }
//
//    @Provides
//    fun provideCalculateGroupUseCase(
//        roomRepo: RoomRepository,
//        explicationRepo: ExplicationRepository
//    ): GroupCalculator {
//        return GroupCalculator(roomRepo, explicationRepo)
//    }
}