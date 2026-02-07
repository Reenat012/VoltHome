package ru.mugalimov.volthome.di.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
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
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.dao.OutboxDao
import ru.mugalimov.volthome.data.local.dao.ProjectDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.dao.RoomsTxDao
import ru.mugalimov.volthome.data.local.dao.TombstoneDao
import ru.mugalimov.volthome.data.local.dao.UuidMapDao
import ru.mugalimov.volthome.data.local.datastore.AppPreferences
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.impl.ManualEditSessionRepositoryImpl
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
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
            .setTransactionExecutor(java.util.concurrent.Executors.newSingleThreadExecutor())
            .addMigrations(
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25, // «санитарная» миграция: выравнивание индексов под Entity
                AppDatabase.MIGRATION_25_26
            )
            // Для клиентов с очень старыми версиями (<16) просто пересоздаём БД
            .fallbackToDestructiveMigrationFrom(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // init if needed
                }
            })
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
    fun provideRoomsTxDao(database: AppDatabase): RoomsTxDao = database.roomsTxDao()

    @Provides
    @Singleton
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideOutboxDao(db: AppDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideTombstoneDao(db: AppDatabase): TombstoneDao = db.tombstoneDao()

    @Provides
    @Singleton
    fun provideUuidMapDao(db: AppDatabase): UuidMapDao = db.uuidMapDao()

    @Provides
    fun provideGroupPhaseOverrideDao(db: AppDatabase): GroupPhaseOverrideDao =
        db.groupPhaseOverrideDao()
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

@Module
@InstallIn(SingletonComponent::class)
object PrefsModule {

    @Provides
    @Singleton
    fun provideAppPreferences(
        @ApplicationContext context: Context
    ): AppPreferences = AppPreferences(context)
}

/**
 * Commit 1: Manual edit session DI
 * Хранение in-memory, singleton на процесс.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ManualEditSessionModule {

    @Binds
    @Singleton
    abstract fun bindManualEditSessionRepository(
        impl: ManualEditSessionRepositoryImpl
    ): ManualEditSessionRepository
}