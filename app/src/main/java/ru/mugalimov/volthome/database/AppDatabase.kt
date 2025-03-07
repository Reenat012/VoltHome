package ru.mugalimov.volthome.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.mugalimov.volthome.dao.RoomDao
import ru.mugalimov.volthome.entity.RoomEntity
import ru.netology.nework.converters.Converters
import kotlin.synchronized

@TypeConverters(Converters::class)
@Database(
    entities = [RoomEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao //доступ к dao

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
    }
}