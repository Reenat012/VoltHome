package ru.mugalimov.volthome.database

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
import ru.mugalimov.volthome.dao.DeviceDao
import ru.mugalimov.volthome.dao.LoadDao
import ru.mugalimov.volthome.dao.RoomDao
import ru.mugalimov.volthome.repository.DeviceRepository
import ru.mugalimov.volthome.repository.LoadsRepository
import ru.mugalimov.volthome.repository.RoomRepository
import ru.mugalimov.volthome.repository.impl.DeviceRepositoryImpl
import ru.mugalimov.volthome.repository.impl.LoadsRepositoryImpl
import ru.mugalimov.volthome.repository.impl.RoomRepositoryImpl
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
//            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideRoomDao(database: AppDatabase): RoomDao = database.roomDao()

    @Provides
    fun provideDeviceDao(database: AppDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun provideLoadDao(database: AppDatabase): LoadDao = database.loadDao()

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
}