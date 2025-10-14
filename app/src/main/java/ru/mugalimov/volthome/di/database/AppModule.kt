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
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.dao.OutboxDao
import ru.mugalimov.volthome.data.local.dao.ProjectDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.dao.RoomsTxDao
import ru.mugalimov.volthome.data.local.dao.TombstoneDao
import ru.mugalimov.volthome.data.local.dao.UuidMapDao
import ru.mugalimov.volthome.data.local.datastore.AppPreferences
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.data.repository.impl.DeviceRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.ExplicationRepositoryImpl
import ru.mugalimov.volthome.data.repository.impl.ProjectsRepositoryImpl
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
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
            .setTransactionExecutor(java.util.concurrent.Executors.newSingleThreadExecutor())
            .addMigrations(
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21   // 🔹 новая миграция
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // init if needed
                }
            })
            // .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideRoomDao(database: AppDatabase): RoomDao = database.roomDao()
    @Provides fun provideDeviceDao(database: AppDatabase): DeviceDao = database.deviceDao()
    @Provides fun provideLoadDao(database: AppDatabase): LoadDao = database.loadDao()
    @Provides fun provideGroupDao(database: AppDatabase): GroupDao = database.groupDao()
    @Provides fun provideGroupDeviceJoinDao(database: AppDatabase): GroupDeviceJoinDao = database.groupDeviceJoinDao()
    @Provides fun provideRoomsTxDao(database: AppDatabase): RoomsTxDao = database.roomsTxDao()
    @Provides @Singleton fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    // 🔹 новые DAO
    @Provides fun provideOutboxDao(db: AppDatabase): OutboxDao = db.outboxDao()
    @Provides fun provideTombstoneDao(db: AppDatabase): TombstoneDao = db.tombstoneDao()

    @Provides
    @Singleton
    fun provideUuidMapDao(db: AppDatabase): UuidMapDao = db.uuidMapDao()
}

// di/RepositoryModule.kt — без изменений по части биндов (оставляю как у тебя)
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindRoomRepository(impl: RoomRepositoryImpl): RoomRepository

    @Binds @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds @Singleton
    abstract fun bindExplicationRepository(impl: ExplicationRepositoryImpl): ExplicationRepository

    @Binds @Singleton
    abstract fun bindProjectsRepository(impl: ProjectsRepositoryImpl): ProjectsRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DefaultsModule {
    @Binds @Singleton
    abstract fun bindDeviceDefaultsProvider(
        impl: StaticDeviceDefaultsProvider
    ): DeviceDefaultsProvider
}

@Module
@InstallIn(SingletonComponent::class)
object PrefsModule {
    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences =
        AppPreferences(context)
}