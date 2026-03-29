package ru.mugalimov.volthome.di.database

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.mugalimov.volthome.data.billing.pending.storage.PendingConfirmStorage
import ru.mugalimov.volthome.data.billing.pending.storage.PendingConfirmStorageImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingPendingModule {

    @Binds
    @Singleton
    abstract fun bindPendingConfirmStorage(
        impl: PendingConfirmStorageImpl
    ): PendingConfirmStorage
}