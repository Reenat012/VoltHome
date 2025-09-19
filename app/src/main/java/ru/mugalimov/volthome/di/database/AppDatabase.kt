package ru.mugalimov.volthome.di.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.mugalimov.volthome.data.local.dao.*
import ru.mugalimov.volthome.data.local.entity.*
import ru.netology.nework.converters.Converters
import kotlin.synchronized

@TypeConverters(Converters::class)
@Database(
    entities = [
        // твои существующие сущности:
        RoomEntity::class,
        DeviceEntity::class,
        LoadEntity::class,
        GroupDeviceJoin::class,
        CircuitGroupEntity::class,
        // новые под проекты и синк:
        ProjectEntity::class,
        ProjectLocalStateEntity::class,
        SyncConflictEntity::class
    ],
    version = 17,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun deviceDao(): DeviceDao
    abstract fun loadDao(): LoadDao
    abstract fun groupDao(): GroupDao
    abstract fun groupDeviceJoinDao(): GroupDeviceJoinDao
    abstract fun roomsTxDao(): RoomsTxDao

    // новые DAO
    abstract fun projectDao(): ProjectDao
    abstract fun projectLocalStateDao(): ProjectLocalStateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MIGRATION_16_17, MIGRATION_17_18)
                    .addCallback(callback)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
    }
}