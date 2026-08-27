package ru.mugalimov.volthome.di.database

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.CableCalculationRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ManualOverridesCleanerRepository
import ru.mugalimov.volthome.data.repository.PanelEquipmentRepository
import ru.mugalimov.volthome.data.repository.PanelLayoutRepository
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.data.repository.SubscriptionRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.data.repository.impl.DeviceRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.CableCalculationRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.ExplicationRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.ManualOverridesCleanerRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.PanelEquipmentRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.PanelLayoutRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.ProjectsRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.ProjectSetupRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.RoomRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.SubscriptionRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.UserPlanRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCableCalculationRepository(
        impl: CableCalculationRepositoryImpl
    ): CableCalculationRepository

    @Binds
    @Singleton
    abstract fun bindPanelEquipmentRepository(
        impl: PanelEquipmentRepositoryImpl
    ): PanelEquipmentRepository

    @Binds
    @Singleton
    abstract fun bindPanelLayoutRepository(
        impl: PanelLayoutRepositoryImpl
    ): PanelLayoutRepository

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
    abstract fun bindProjectSetupRepository(
        impl: ProjectSetupRepositoryImpl
    ): ProjectSetupRepository

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

    @Binds
    @Singleton
    abstract fun bindManualOverridesCleanerRepository(
        impl: ManualOverridesCleanerRepositoryImpl
    ): ManualOverridesCleanerRepository
}
