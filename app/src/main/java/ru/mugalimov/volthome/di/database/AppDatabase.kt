package ru.mugalimov.volthome.di.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.mugalimov.volthome.data.local.dao.*
import ru.mugalimov.volthome.data.local.entity.*
import ru.netology.nework.converters.Converters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.synchronized

@TypeConverters(Converters::class)
@Database(
    entities = [
        // существующие сущности:
        RoomEntity::class,
        DeviceEntity::class,
        LoadEntity::class,
        GroupDeviceJoin::class,
        CircuitGroupEntity::class,

        // под проекты и синк:
        ProjectEntity::class,
        ProjectLocalStateEntity::class,
        SyncConflictEntity::class
    ],
    version = 18,
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

        // Если у тебя уже была своя MIGRATION_16_17 — оставь. Иначе — no-op.
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op в рамках этой поставки
            }
        }

        /**
         * 17 → 18:
         *  - Добавляем колонку project_id (TEXT NULL) в rooms/devices/groups/loads
         *  - Индекс по project_id
         *  - Создаём Default Project и проставляем его id во все существующие строки
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                // 1) Добавляем project_id в основные таблицы (если ещё нет)
                safeAddColumn(db, "rooms", "project_id", "TEXT")
                safeAddColumn(db, "devices", "project_id", "TEXT")
                safeAddColumn(db, "groups", "project_id", "TEXT")
                safeAddColumn(db, "loads", "project_id", "TEXT")

                // 2) Индексы по project_id (idempotent)
                safeCreateIndex(db, "idx_rooms_project_id", "rooms", "project_id")
                safeCreateIndex(db, "idx_devices_project_id", "devices", "project_id")
                safeCreateIndex(db, "idx_groups_project_id", "groups", "project_id")
                safeCreateIndex(db, "idx_loads_project_id", "loads", "project_id")

                // 3) Создаём Default Project (если таблица projects есть и записи с таким id нет)
                val defaultProjectId = UUID.randomUUID().toString()
                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

                db.execSQL(
                    """
                    INSERT INTO projects (id, name, note, version, updated_at, is_deleted)
                    SELECT ?, 'Проект по умолчанию', NULL, 1, ?, 0
                    WHERE NOT EXISTS (SELECT 1 FROM projects WHERE id = ?)
                    """.trimIndent(),
                    arrayOf(defaultProjectId, nowIso, defaultProjectId)
                )

                // 4) Проставляем во все строки, где project_id IS NULL
                db.execSQL("UPDATE rooms  SET project_id = COALESCE(project_id, ?) WHERE project_id IS NULL", arrayOf(defaultProjectId))
                db.execSQL("UPDATE devices SET project_id = COALESCE(project_id, ?) WHERE project_id IS NULL", arrayOf(defaultProjectId))
                db.execSQL("UPDATE groups  SET project_id = COALESCE(project_id, ?) WHERE project_id IS NULL", arrayOf(defaultProjectId))
                db.execSQL("UPDATE loads   SET project_id = COALESCE(project_id, ?) WHERE project_id IS NULL", arrayOf(defaultProjectId))

                db.execSQL("PRAGMA foreign_keys=ON")
            }

            private fun safeAddColumn(db: SupportSQLiteDatabase, table: String, col: String, type: String) {
                // Проверяем, есть ли колонка
                val cursor = db.query("PRAGMA table_info($table)")
                var exists = false
                cursor.use {
                    val nameIdx = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        if (it.getString(nameIdx) == col) { exists = true; break }
                    }
                }
                if (!exists) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN $col $type")
                }
            }

            private fun safeCreateIndex(db: SupportSQLiteDatabase, indexName: String, table: String, col: String) {
                // SQLite не имеет IF NOT EXISTS для CREATE INDEX до некоторых версий,
                // поэтому просто пробуем создать и ловим ошибку — Room её проглотит.
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS $indexName ON $table($col)")
                } catch (_: Throwable) { /* ignore */ }
            }
        }
    }
}