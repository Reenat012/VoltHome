package ru.mugalimov.volthome.di.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase.Callback
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.dao.RoomsTxDao
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.LoadsRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.data.repository.impl.DeviceRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.ExplicationRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.LoadsRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.RoomRepositoryImpl
import ru.mugalimov.volthome.domain.model.provider.DeviceDefaultsProvider
import ru.mugalimov.volthome.domain.model.provider.StaticDeviceDefaultsProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "volthome.db"
        )
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Инициализация БД при первом создании
                }
            })
            // Использовать для пересоздания БД при ошибке миграции
            // TODO после использования закомментировать иначе БД будет постоянно пересоздаваться
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideRoomDao(database: AppDatabase): RoomDao = database.roomDao()

    @Provides
    fun provideDeviceDao(database: AppDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun provideLoadDao(database: AppDatabase): LoadDao = database.loadDao()

    @Provides
    fun provideGroupDao(database: AppDatabase): GroupDao = database.groupDao()

    @Provides
    fun provideGroupDeviceJoinDao(database: AppDatabase): GroupDeviceJoinDao =
        database.groupDeviceJoinDao()

    @Provides
    fun provideRoomsTxDao(database: AppDatabase): RoomsTxDao =
        database.roomsTxDao()
}

// di/RepositoryModule.kt

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindRoomRepository(
        impl: RoomRepositoryImpl
    ): RoomRepository

    @Binds
    abstract fun bindDeviceRepository(
        impl: DeviceRepositoryImpl
    ): DeviceRepository

    @Binds
    abstract fun bindLoadRepository(impl: LoadsRepositoryImpl): LoadsRepository

    @Binds
    abstract fun bindExplicationRepository(impl: ExplicationRepositoryImpl): ExplicationRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DefaultsModule {
    @Binds
    @Singleton
    abstract fun bindDeviceDefaultsProvider(
        impl: StaticDeviceDefaultsProvider
    ): DeviceDefaultsProvider
}