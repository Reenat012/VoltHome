package ru.mugalimov.volthome.di.database

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.mugalimov.volthome.data.billing.RustoreBillingManager
import ru.mugalimov.volthome.data.billing.RustoreBillingManagerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindRustoreBillingManager(
        impl: RustoreBillingManagerImpl
    ): RustoreBillingManager
}