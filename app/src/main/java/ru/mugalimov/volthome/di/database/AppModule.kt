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
import ru.mugalimov.volthome.data.local.dao.ApparatusSelectionDao
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.CableCalculationDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.local.dao.ProjectDao
import ru.mugalimov.volthome.data.local.dao.ProjectLocalStateDao
import ru.mugalimov.volthome.data.local.dao.ProjectSetupDao
import ru.mugalimov.volthome.data.local.dao.PanelLayoutDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.dao.RoomsTxDao
import ru.mugalimov.volthome.data.local.datastore.AppPreferences
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.impl.ManualEditSessionRepositoryImpl
import ru.mugalimov.volthome.domain.model.provider.ApparatusCatalogProvider
import ru.mugalimov.volthome.domain.model.provider.DeviceDefaultsProvider
import ru.mugalimov.volthome.domain.model.provider.JsonApparatusCatalogProvider
import ru.mugalimov.volthome.domain.model.provider.JsonDeviceDefaultsProvider
import ru.mugalimov.volthome.domain.model.provider.JsonProtectionPriceCatalogProvider
import ru.mugalimov.volthome.domain.model.provider.ProtectionPriceCatalogProvider
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
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
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
    fun provideGroupPhaseOverrideDao(db: AppDatabase): GroupPhaseOverrideDao =
        db.groupPhaseOverrideDao()

    @Provides
    fun provideApparatusSelectionDao(db: AppDatabase): ApparatusSelectionDao =
        db.apparatusSelectionDao()

    @Provides
    fun providePanelLayoutDao(db: AppDatabase): PanelLayoutDao = db.panelLayoutDao()

    @Provides
    @Singleton
    fun provideProjectLocalStateDao(db: AppDatabase): ProjectLocalStateDao {
        return db.projectLocalStateDao()
    }

    @Provides
    @Singleton
    fun provideProjectSetupDao(db: AppDatabase): ProjectSetupDao = db.projectSetupDao()

    @Provides
    @Singleton
    fun provideCableCalculationDao(db: AppDatabase): CableCalculationDao = db.cableCalculationDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DefaultsModule {

    @Binds
    @Singleton
    abstract fun bindDeviceDefaultsProvider(
        impl: JsonDeviceDefaultsProvider
    ): DeviceDefaultsProvider

    @Binds
    @Singleton
    abstract fun bindProtectionPriceCatalogProvider(
        impl: JsonProtectionPriceCatalogProvider
    ): ProtectionPriceCatalogProvider

    @Binds
    @Singleton
    abstract fun bindApparatusCatalogProvider(
        impl: JsonApparatusCatalogProvider
    ): ApparatusCatalogProvider
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
