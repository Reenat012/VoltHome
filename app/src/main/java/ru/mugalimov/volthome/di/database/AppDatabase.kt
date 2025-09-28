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
        SyncConflictEntity::class,

        UuidMapRoom::class,
        UuidMapGroup::class,
        UuidMapDevice::class
    ],
    version = 20,
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

    abstract fun uuidMapDao(): UuidMapDao

    companion object {
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
                val nowIso =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

                db.execSQL(
                    """
                    INSERT INTO projects (id, name, note, version, updated_at, is_deleted)
                    SELECT ?, 'Проект по умолчанию', NULL, 1, ?, 0
                    WHERE NOT EXISTS (SELECT 1 FROM projects WHERE id = ?)
                    """.trimIndent(),
                    arrayOf(defaultProjectId, nowIso, defaultProjectId)
                )

                // 4) Проставляем во все строки, где project_id IS NULL
                db.execSQL(
                    "UPDATE rooms  SET project_id = COALESCE(project_id, ?) WHERE project_id IS NULL",
                    arrayOf(defaultProjectId)
                )
                db.execSQL(
                    "UPDATE devices SET project_id = COALESCE(project_id, ?) WHERE project_id IS NULL",
                    arrayOf(defaultProjectId)
                )
                db.execSQL(
                    "UPDATE groups  SET project_id = COALESCE(project_id, ?) WHERE project_id IS NULL",
                    arrayOf(defaultProjectId)
                )
                db.execSQL(
                    "UPDATE loads   SET project_id = COALESCE(project_id, ?) WHERE project_id IS NULL",
                    arrayOf(defaultProjectId)
                )

                db.execSQL("PRAGMA foreign_keys=ON")
            }

            private fun safeAddColumn(
                db: SupportSQLiteDatabase,
                table: String,
                col: String,
                type: String
            ) {
                // Проверяем, есть ли колонка
                val cursor = db.query("PRAGMA table_info($table)")
                var exists = false
                cursor.use {
                    val nameIdx = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        if (it.getString(nameIdx) == col) {
                            exists = true; break
                        }
                    }
                }
                if (!exists) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN $col $type")
                }
            }

            private fun safeCreateIndex(
                db: SupportSQLiteDatabase,
                indexName: String,
                table: String,
                col: String
            ) {
                // SQLite не имеет IF NOT EXISTS для CREATE INDEX до некоторых версий,
                // поэтому просто пробуем создать и ловим ошибку — Room её проглотит.
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS $indexName ON $table($col)")
                } catch (_: Throwable) { /* ignore */
                }
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun hasTable(name: String): Boolean {
                    val c = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name))
                    c.use { return it.moveToFirst() }
                }

                // ---- rooms ----
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS uuid_map_rooms_tmp (
                room_uuid TEXT NOT NULL PRIMARY KEY,
                local_id  INTEGER NOT NULL
            )
        """.trimIndent())
                if (hasTable("uuid_map_rooms")) {
                    db.execSQL("""
                INSERT OR IGNORE INTO uuid_map_rooms_tmp(room_uuid, local_id)
                SELECT room_uuid, local_id FROM uuid_map_rooms
                WHERE room_uuid IS NOT NULL
            """.trimIndent())
                    // сносим старый индекс/таблицу, если были
                    try { db.execSQL("DROP INDEX IF EXISTS idx_uuid_map_rooms_local") } catch (_: Throwable) {}
                    try { db.execSQL("DROP TABLE IF EXISTS uuid_map_rooms") } catch (_: Throwable) {}
                }
                db.execSQL("ALTER TABLE uuid_map_rooms_tmp RENAME TO uuid_map_rooms")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_uuid_map_rooms_local_id ON uuid_map_rooms(local_id)")

                // ---- groups ----
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS uuid_map_groups_tmp (
                group_uuid TEXT NOT NULL PRIMARY KEY,
                local_id   INTEGER NOT NULL
            )
        """.trimIndent())
                if (hasTable("uuid_map_groups")) {
                    db.execSQL("""
                INSERT OR IGNORE INTO uuid_map_groups_tmp(group_uuid, local_id)
                SELECT group_uuid, local_id FROM uuid_map_groups
                WHERE group_uuid IS NOT NULL
            """.trimIndent())
                    try { db.execSQL("DROP INDEX IF EXISTS idx_uuid_map_groups_local") } catch (_: Throwable) {}
                    try { db.execSQL("DROP TABLE IF EXISTS uuid_map_groups") } catch (_: Throwable) {}
                }
                db.execSQL("ALTER TABLE uuid_map_groups_tmp RENAME TO uuid_map_groups")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_uuid_map_groups_local_id ON uuid_map_groups(local_id)")

                // ---- devices ----
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS uuid_map_devices_tmp (
                device_uuid TEXT NOT NULL PRIMARY KEY,
                local_id    INTEGER NOT NULL
            )
        """.trimIndent())
                if (hasTable("uuid_map_devices")) {
                    db.execSQL("""
                INSERT OR IGNORE INTO uuid_map_devices_tmp(device_uuid, local_id)
                SELECT device_uuid, local_id FROM uuid_map_devices
                WHERE device_uuid IS NOT NULL
            """.trimIndent())
                    try { db.execSQL("DROP INDEX IF EXISTS idx_uuid_map_devices_local") } catch (_: Throwable) {}
                    try { db.execSQL("DROP TABLE IF EXISTS uuid_map_devices") } catch (_: Throwable) {}
                }
                db.execSQL("ALTER TABLE uuid_map_devices_tmp RENAME TO uuid_map_devices")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_uuid_map_devices_local_id ON uuid_map_devices(local_id)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // На время миграции отключаем FK, чтобы спокойно перенести данные
                db.execSQL("PRAGMA foreign_keys=OFF")

                // 1) Создаём новую таблицу devices_tmp с нужной схемой:
                //    - room_id NULL
                //    - FK(room_id) → rooms(id) ON DELETE SET NULL
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS devices_tmp (
                device_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                power INTEGER NOT NULL,
                voltage TEXT NOT NULL,
                demand_ratio REAL NOT NULL,
                created_at INTEGER NOT NULL,
                room_id INTEGER NULL,
                device_type TEXT NOT NULL,
                power_factor REAL NOT NULL,
                has_motor INTEGER NOT NULL DEFAULT 0,
                requires_dedicated INTEGER NOT NULL DEFAULT 0,
                requires_socket INTEGER NOT NULL DEFAULT 1,
                project_id TEXT NULL,
                FOREIGN KEY(room_id) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
                )

                // 2) Переносим данные из старой devices в devices_tmp 1:1
                db.execSQL(
                    """
            INSERT INTO devices_tmp (
                device_id, name, power, voltage, demand_ratio, created_at,
                room_id, device_type, power_factor, has_motor,
                requires_dedicated, requires_socket, project_id
            )
            SELECT
                device_id, name, power, voltage, demand_ratio, created_at,
                room_id, device_type, power_factor, has_motor,
                requires_dedicated, requires_socket, project_id
            FROM devices
            """.trimIndent()
                )

                // 3) Сносим старые индексы (если были)
                try { db.execSQL("DROP INDEX IF EXISTS idx_devices_room_id") } catch (_: Throwable) {}
                try { db.execSQL("DROP INDEX IF EXISTS idx_devices_project_id") } catch (_: Throwable) {}

                // 4) Заменяем таблицу
                db.execSQL("DROP TABLE devices")
                db.execSQL("ALTER TABLE devices_tmp RENAME TO devices")

                // 5) Восстанавливаем индексы в соответствии со схемой Entity
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_devices_room_id ON devices(room_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_devices_project_id ON devices(project_id)")

                // Возвращаем контроль ссылочной целостности
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }
    }
}