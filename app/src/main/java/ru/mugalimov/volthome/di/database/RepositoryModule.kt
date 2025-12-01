package ru.mugalimov.volthome.di.database

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.data.repository.SubscriptionRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.data.repository.impl.DeviceRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.ExplicationRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.ProjectsRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.RoomRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.SubscriptionRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.UserPlanRepositoryImpl
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindRoomRepository(impl: RoomRepositoryImpl): RoomRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindExplicationRepository(impl: ExplicationRepositoryImpl): ExplicationRepository

    @Binds
    @Singleton
    abstract fun bindProjectsRepository(impl: ProjectsRepositoryImpl): ProjectsRepository

    @Binds
    @Singleton
    abstract fun bindUserPlanRepository(
        impl: UserPlanRepositoryImpl
    ): UserPlanRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        impl: SubscriptionRepositoryImpl
    ): SubscriptionRepository
}