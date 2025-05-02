package ru.mugalimov.volthome.di.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.netology.nework.converters.Converters
import kotlin.synchronized

@TypeConverters(Converters::class)
@Database(
    entities = [
        RoomEntity::class,
        DeviceEntity::class,
        LoadEntity::class,
        GroupDeviceJoin::class,
        CircuitGroupEntity::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao //доступ к dao
    abstract fun deviceDao(): DeviceDao
    abstract fun loadDao(): LoadDao
    abstract fun groupDao(): GroupDao
    abstract fun groupDeviceJoinDao(): GroupDeviceJoinDao

    companion object {
        //Singlton-паттерн для экземпляра БД, хранит единственный экземпляр БД
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database" // Имя файла БД
                ).build()
                INSTANCE = instance
                instance
            }
        }

        private val callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("PRAGMA foreign_keys = ON") // Активируем FK
            }
        }
    }
}