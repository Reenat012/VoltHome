package ru.mugalimov.volthome.di.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.dao.OutboxDao
import ru.mugalimov.volthome.data.local.dao.ProjectDao
import ru.mugalimov.volthome.data.local.dao.ProjectLocalStateDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.dao.RoomsTxDao
import ru.mugalimov.volthome.data.local.dao.TombstoneDao
import ru.mugalimov.volthome.data.local.dao.UuidMapDao
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.data.local.entity.OutboxEntity
import ru.mugalimov.volthome.data.local.entity.ProjectEntity
import ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.local.entity.SyncConflictEntity
import ru.mugalimov.volthome.data.local.entity.TombstoneEntity
import ru.mugalimov.volthome.data.local.entity.UuidMapDevice
import ru.mugalimov.volthome.data.local.entity.UuidMapGroup
import ru.mugalimov.volthome.data.local.entity.UuidMapRoom
import ru.netology.nework.converters.Converters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
        UuidMapDevice::class,

        // новые:
        OutboxEntity::class,
        TombstoneEntity::class
    ],
    version = 25, // подняли под «санитарную» миграцию 24 → 25
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun roomDao(): RoomDao
    abstract fun deviceDao(): DeviceDao
    abstract fun loadDao(): LoadDao
    abstract fun groupDao(): GroupDao
    abstract fun groupDeviceJoinDao(): GroupDeviceJoinDao
    abstract fun roomsTxDao(): RoomsTxDao

    // проекты / синк
    abstract fun projectDao(): ProjectDao
    abstract fun projectLocalStateDao(): ProjectLocalStateDao

    abstract fun uuidMapDao(): UuidMapDao

    // outbox / tombstones
    abstract fun outboxDao(): OutboxDao
    abstract fun tombstoneDao(): TombstoneDao

    companion object {

        // ======== существующие миграции ========

        // ======== missing legacy migrations ========

        // 13 → 14
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op
                // версия 14 не меняла схему, только версию БД
            }
        }

        // 14 → 15
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op
                // версия 15 не меняла схему, только версию БД
            }
        }

        // 15 → 16
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op
                // версия 16 — база для MIGRATION_16_17
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                safeAddColumn(db, "rooms", "project_id", "TEXT")
                safeAddColumn(db, "devices", "project_id", "TEXT")
                safeAddColumn(db, "groups", "project_id", "TEXT")
                safeAddColumn(db, "loads", "project_id", "TEXT")

                safeCreateIndex(db, "idx_rooms_project_id", "rooms", "project_id")
                safeCreateIndex(db, "idx_devices_project_id", "devices", "project_id")
                safeCreateIndex(db, "idx_groups_project_id", "groups", "project_id")
                safeCreateIndex(db, "idx_loads_project_id", "loads", "project_id")
                // добавляем индекс, которого не хватало в старой схеме
                safeCreateIndex(db, "idx_loads_room_id", "loads", "room_id")

                val defaultProjectId = UUID.randomUUID().toString()
                val nowIso = SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    Locale.US
                ).format(Date())

                // Safety: создать таблицу projects, если её нет
                try {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `projects` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `name` TEXT NOT NULL,
                            `note` TEXT,
                            `version` INTEGER NOT NULL,
                            `updated_at` TEXT NOT NULL,
                            `is_deleted` INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `idx_projects_is_deleted` " +
                                "ON `projects`(`is_deleted`)"
                    )
                } catch (_: Throwable) {
                    // игнорируем
                }

                db.execSQL(
                    """
                    INSERT INTO `projects` (`id`, `name`, `note`, `version`, `updated_at`, `is_deleted`)
                    SELECT ?, 'Проект по умолчанию', NULL, 1, ?, 0
                    WHERE NOT EXISTS (SELECT 1 FROM `projects` WHERE `id` = ?)
                    """.trimIndent(),
                    arrayOf(defaultProjectId, nowIso, defaultProjectId)
                )

                db.execSQL(
                    "UPDATE `rooms`  SET `project_id` = COALESCE(`project_id`, ?) WHERE `project_id` IS NULL",
                    arrayOf(defaultProjectId)
                )
                db.execSQL(
                    "UPDATE `devices` SET `project_id` = COALESCE(`project_id`, ?) WHERE `project_id` IS NULL",
                    arrayOf(defaultProjectId)
                )
                db.execSQL(
                    "UPDATE `groups`  SET `project_id` = COALESCE(`project_id`, ?) WHERE `project_id` IS NULL",
                    arrayOf(defaultProjectId)
                )
                db.execSQL(
                    "UPDATE `loads`   SET `project_id` = COALESCE(`project_id`, ?) WHERE `project_id` IS NULL",
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
                val cursor = db.query("PRAGMA table_info($table)")
                var exists = false
                cursor.use {
                    val nameIdx = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        if (it.getString(nameIdx) == col) {
                            exists = true
                            break
                        }
                    }
                }
                if (!exists) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `$col` $type")
                }
            }

            private fun safeCreateIndex(
                db: SupportSQLiteDatabase,
                indexName: String,
                table: String,
                col: String
            ) {
                try {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `$indexName` " +
                                "ON `$table`(`$col`)"
                    )
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun hasTable(name: String): Boolean {
                    val c = db.query(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                        arrayOf(name)
                    )
                    c.use { return it.moveToFirst() }
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `uuid_map_rooms_tmp` (
                        `room_uuid` TEXT NOT NULL PRIMARY KEY,
                        `local_id`  INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                if (hasTable("uuid_map_rooms")) {
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `uuid_map_rooms_tmp`(`room_uuid`, `local_id`)
                        SELECT `room_uuid`, `local_id` FROM `uuid_map_rooms`
                        WHERE `room_uuid` IS NOT NULL
                        """.trimIndent()
                    )
                    try {
                        db.execSQL("DROP INDEX IF EXISTS `idx_uuid_map_rooms_local`")
                    } catch (_: Throwable) {
                    }
                    try {
                        db.execSQL("DROP TABLE IF EXISTS `uuid_map_rooms`")
                    } catch (_: Throwable) {
                    }
                }
                db.execSQL("ALTER TABLE `uuid_map_rooms_tmp` RENAME TO `uuid_map_rooms`")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_uuid_map_rooms_local_id`
                    ON `uuid_map_rooms`(`local_id`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `uuid_map_groups_tmp` (
                        `group_uuid` TEXT NOT NULL PRIMARY KEY,
                        `local_id`   INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                if (hasTable("uuid_map_groups")) {
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `uuid_map_groups_tmp`(`group_uuid`, `local_id`)
                        SELECT `group_uuid`, `local_id` FROM `uuid_map_groups`
                        WHERE `group_uuid` IS NOT NULL
                        """.trimIndent()
                    )
                    try {
                        db.execSQL("DROP INDEX IF EXISTS `idx_uuid_map_groups_local`")
                    } catch (_: Throwable) {
                    }
                    try {
                        db.execSQL("DROP TABLE IF EXISTS `uuid_map_groups`")
                    } catch (_: Throwable) {
                    }
                }
                db.execSQL("ALTER TABLE `uuid_map_groups_tmp` RENAME TO `uuid_map_groups`")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_uuid_map_groups_local_id`
                    ON `uuid_map_groups`(`local_id`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `uuid_map_devices_tmp` (
                        `device_uuid` TEXT NOT NULL PRIMARY KEY,
                        `local_id`    INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                if (hasTable("uuid_map_devices")) {
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `uuid_map_devices_tmp`(`device_uuid`, `local_id`)
                        SELECT `device_uuid`, `local_id` FROM `uuid_map_devices`
                        WHERE `device_uuid` IS NOT NULL
                        """.trimIndent()
                    )
                    try {
                        db.execSQL("DROP INDEX IF EXISTS `idx_uuid_map_devices_local`")
                    } catch (_: Throwable) {
                    }
                    try {
                        db.execSQL("DROP TABLE IF EXISTS `uuid_map_devices`")
                    } catch (_: Throwable) {
                    }
                }
                db.execSQL("ALTER TABLE `uuid_map_devices_tmp` RENAME TO `uuid_map_devices`")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_uuid_map_devices_local_id`
                    ON `uuid_map_devices`(`local_id`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `devices_tmp` (
                        `device_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `power` INTEGER NOT NULL,
                        `voltage` TEXT NOT NULL,
                        `demand_ratio` REAL NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `room_id` INTEGER NULL,
                        `device_type` TEXT NOT NULL,
                        `power_factor` REAL NOT NULL,
                        `has_motor` INTEGER NOT NULL DEFAULT 0,
                        `requires_dedicated` INTEGER NOT NULL DEFAULT 0,
                        `requires_socket` INTEGER NOT NULL DEFAULT 1,
                        `project_id` TEXT NULL,
                        FOREIGN KEY(`room_id`) REFERENCES `rooms`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `devices_tmp` (
                        `device_id`, `name`, `power`, `voltage`, `demand_ratio`, `created_at`,
                        `room_id`, `device_type`, `power_factor`, `has_motor`,
                        `requires_dedicated`, `requires_socket`, `project_id`
                    )
                    SELECT
                        `device_id`, `name`, `power`, `voltage`, `demand_ratio`, `created_at`,
                        `room_id`, `device_type`, `power_factor`, `has_motor`,
                        `requires_dedicated`, `requires_socket`, `project_id`
                    FROM `devices`
                    """.trimIndent()
                )

                try {
                    db.execSQL("DROP INDEX IF EXISTS `idx_devices_room_id`")
                } catch (_: Throwable) {
                }
                try {
                    db.execSQL("DROP INDEX IF EXISTS `idx_devices_project_id`")
                } catch (_: Throwable) {
                }

                db.execSQL("DROP TABLE `devices`")
                db.execSQL("ALTER TABLE `devices_tmp` RENAME TO `devices`")

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_devices_room_id` ON `devices`(`room_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_devices_project_id` ON `devices`(`project_id`)"
                )

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        // 20 → 21: создаём outbox и tombstones
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Outbox
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `outbox` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `project_id` TEXT NULL,
                        `op_type` TEXT NOT NULL,
                        `payload_json` TEXT NOT NULL,
                        `requires_online` INTEGER NOT NULL DEFAULT 1,
                        `state` TEXT NOT NULL,
                        `attempt` INTEGER NOT NULL DEFAULT 0,
                        `last_error` TEXT NULL,
                        `group_key` TEXT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_outbox_state_created_at`
                    ON `outbox`(`state`, `created_at`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_outbox_project_state`
                    ON `outbox`(`project_id`, `state`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_outbox_group_key`
                    ON `outbox`(`group_key`)
                    """.trimIndent()
                )

                // Tombstones
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tombstones` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `project_id` TEXT NULL,
                        `entity_type` TEXT NOT NULL,
                        `local_id` INTEGER NULL,
                        `server_uuid` TEXT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_tombstones_type_project`
                    ON `tombstones`(`entity_type`, `project_id`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_tombstones_type_local`
                    ON `tombstones`(`entity_type`, `local_id`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_tombstones_type_uuid`
                    ON `tombstones`(`entity_type`, `server_uuid`)
                    """.trimIndent()
                )
            }
        }

        // 21 → 22: индексы устройств
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun dropIndexIfExists(name: String) {
                    try {
                        db.execSQL("DROP INDEX IF EXISTS `$name`")
                    } catch (_: Throwable) {
                    }
                }

                listOf(
                    "ux_devices_room_name",
                    "ux_devices_room_name_alive",
                    "ux_devices_project_room_name",
                    "ux_devices_project_room_name_alive",
                    "ux_devices_name_unique",
                    "devices_name_unique"
                ).forEach { dropIndexIfExists(it) }

                val explicitUnique = db.query(
                    """
                    SELECT name FROM sqlite_master
                    WHERE type='index'
                      AND tbl_name='devices'
                      AND sql LIKE '%UNIQUE%'
                      AND sql LIKE '%name%'
                    """.trimIndent()
                ).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(c.getString(0) ?: "")
                        }
                    }
                }
                explicitUnique
                    .filter { it.isNotBlank() }
                    .forEach { dropIndexIfExists(it) }

                val stillUnique = db.query(
                    """
                    SELECT 1 FROM sqlite_master
                    WHERE type='index'
                      AND tbl_name='devices'
                      AND sql LIKE '%UNIQUE%'
                      AND sql LIKE '%name%'
                    """.trimIndent()
                ).use { it.moveToFirst() }

                if (stillUnique) {
                    db.execSQL("PRAGMA foreign_keys=OFF")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `devices_new` (
                            `device_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `power` INTEGER NOT NULL,
                            `voltage` TEXT NOT NULL,
                            `demand_ratio` REAL NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `room_id` INTEGER NULL,
                            `device_type` TEXT NOT NULL,
                            `power_factor` REAL NOT NULL,
                            `has_motor` INTEGER NOT NULL DEFAULT 0,
                            `requires_dedicated` INTEGER NOT NULL DEFAULT 0,
                            `requires_socket` INTEGER NOT NULL DEFAULT 1,
                            `project_id` TEXT NULL,
                            FOREIGN KEY(`room_id`) REFERENCES `rooms`(`id`)
                                ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        INSERT INTO `devices_new` (
                            `device_id`, `name`, `power`, `voltage`, `demand_ratio`, `created_at`,
                            `room_id`, `device_type`, `power_factor`, `has_motor`,
                            `requires_dedicated`, `requires_socket`, `project_id`
                        )
                        SELECT
                            `device_id`, `name`, `power`, `voltage`, `demand_ratio`, `created_at`,
                            `room_id`, `device_type`, `power_factor`, `has_motor`,
                            `requires_dedicated`, `requires_socket`, `project_id`
                        FROM `devices`
                        """.trimIndent()
                    )

                    try {
                        db.execSQL("DROP TABLE `devices`")
                    } catch (_: Throwable) {
                    }
                    db.execSQL("ALTER TABLE `devices_new` RENAME TO `devices`")

                    db.execSQL("PRAGMA foreign_keys=ON")
                }

                try {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `idx_devices_name` ON `devices`(`name`)"
                    )
                } catch (_: Throwable) {
                }
                try {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `idx_devices_room_id` ON `devices`(`room_id`)"
                    )
                } catch (_: Throwable) {
                }
                try {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `idx_devices_project_id` ON `devices`(`project_id`)"
                    )
                } catch (_: Throwable) {
                }
            }
        }

        // 22 → 23: фиксация identity hash через индексы устройств
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun createIndexSafe(sql: String) {
                    try {
                        db.execSQL(sql)
                    } catch (_: Throwable) {
                        // игнорируем
                    }
                }

                createIndexSafe(
                    "CREATE INDEX IF NOT EXISTS `idx_devices_name` ON `devices`(`name`)"
                )
                createIndexSafe(
                    "CREATE INDEX IF NOT EXISTS `idx_devices_room_id` ON `devices`(`room_id`)"
                )
                createIndexSafe(
                    "CREATE INDEX IF NOT EXISTS `idx_devices_project_id` ON `devices`(`project_id`)"
                )
            }
        }

        // 23 → 24: фиксим индексы rooms под RoomEntity
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Сносим старый уникальный индекс по name, если он есть
                try {
                    db.execSQL("DROP INDEX IF EXISTS `index_rooms_name`")
                } catch (_: Throwable) {
                }

                // На всякий случай — дропаем возможный кривой uq_rooms_name_project
                try {
                    db.execSQL("DROP INDEX IF EXISTS `uq_rooms_name_project`")
                } catch (_: Throwable) {
                }

                // Создаём тот индекс, который ожидает RoomEntity: UNIQUE(name, project_id)
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `uq_rooms_name_project`
                    ON `rooms`(`name`, `project_id`)
                    """.trimIndent()
                )
            }
        }

        // 24 → 25: «санитарная» миграция — приводим индексы и вспомогательные таблицы под актуальные Entity
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {

                fun dropIndexIfExists(name: String) {
                    try {
                        db.execSQL("DROP INDEX IF EXISTS `$name`")
                    } catch (_: Throwable) {
                        // нам важен итоговый результат, а не статус старых индексов
                    }
                }

                fun createIndexSafe(sql: String) {
                    try {
                        db.execSQL(sql)
                    } catch (_: Throwable) {
                        // если индекс уже есть / что-то помешало — не роняем миграцию
                    }
                }

                // ===== ROOMS =====
                dropIndexIfExists("index_rooms_name")
                dropIndexIfExists("uq_rooms_name_project")

                createIndexSafe(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `uq_rooms_name_project`
                    ON `rooms`(`name`, `project_id`)
                    """.trimIndent()
                )
                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `idx_rooms_project_id`
                    ON `rooms`(`project_id`)
                    """.trimIndent()
                )

                // ===== LOADS =====
                dropIndexIfExists("index_loads_room_id")
                dropIndexIfExists("idx_loads_room_id")
                dropIndexIfExists("index_loads_project_id")
                dropIndexIfExists("idx_loads_project_id")

                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `idx_loads_room_id`
                    ON `loads`(`room_id`)
                    """.trimIndent()
                )
                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `idx_loads_project_id`
                    ON `loads`(`project_id`)
                    """.trimIndent()
                )

                // ===== GROUPS =====
                dropIndexIfExists("index_groups_room_id")
                dropIndexIfExists("idx_groups_room_id")
                dropIndexIfExists("index_groups_project_id")
                dropIndexIfExists("idx_groups_project_id")

                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `idx_groups_room_id`
                    ON `groups`(`room_id`)
                    """.trimIndent()
                )
                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `idx_groups_project_id`
                    ON `groups`(`project_id`)
                    """.trimIndent()
                )

                // ===== DEVICES =====
                listOf(
                    "ux_devices_room_name",
                    "ux_devices_room_name_alive",
                    "ux_devices_project_room_name",
                    "ux_devices_project_room_name_alive",
                    "ux_devices_name_unique",
                    "devices_name_unique"
                ).forEach { dropIndexIfExists(it) }

                dropIndexIfExists("idx_devices_name")
                dropIndexIfExists("idx_devices_room_id")
                dropIndexIfExists("idx_devices_project_id")
                dropIndexIfExists("index_devices_name")
                dropIndexIfExists("index_devices_room_id")
                dropIndexIfExists("index_devices_project_id")

                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `idx_devices_name`
                    ON `devices`(`name`)
                    """.trimIndent()
                )
                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `idx_devices_room_id`
                    ON `devices`(`room_id`)
                    """.trimIndent()
                )
                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `idx_devices_project_id`
                    ON `devices`(`project_id`)
                    """.trimIndent()
                )

                // ===== PROJECTS =====
                dropIndexIfExists("idx_projects_is_deleted")

                // ===== OUTBOX =====
                dropIndexIfExists("index_outbox_state_created_at")
                dropIndexIfExists("index_outbox_project_state")
                dropIndexIfExists("index_outbox_project_id_state")
                dropIndexIfExists("index_outbox_group_key")

                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `index_outbox_state_created_at`
                    ON `outbox`(`state`, `created_at`)
                    """.trimIndent()
                )
                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `index_outbox_project_id_state`
                    ON `outbox`(`project_id`, `state`)
                    """.trimIndent()
                )
                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `index_outbox_group_key`
                    ON `outbox`(`group_key`)
                    """.trimIndent()
                )

                // ===== TOMBSTONES =====
                dropIndexIfExists("index_tombstones_type_project")
                dropIndexIfExists("index_tombstones_type_local")
                dropIndexIfExists("index_tombstones_type_uuid")

                dropIndexIfExists("index_tombstones_entity_type_project_id")
                dropIndexIfExists("index_tombstones_entity_type_local_id")
                dropIndexIfExists("index_tombstones_entity_type_server_uuid")

                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `index_tombstones_entity_type_project_id`
                    ON `tombstones`(`entity_type`, `project_id`)
                    """.trimIndent()
                )
                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `index_tombstones_entity_type_local_id`
                    ON `tombstones`(`entity_type`, `local_id`)
                    """.trimIndent()
                )
                createIndexSafe(
                    """
                    CREATE INDEX IF NOT EXISTS `index_tombstones_entity_type_server_uuid`
                    ON `tombstones`(`entity_type`, `server_uuid`)
                    """.trimIndent()
                )

                // ===== PROJECT_LOCAL_STATE =====
                // Таблица для ProjectLocalStateEntity, если её не было в старой базе
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `project_local_state` (
                        `project_id` TEXT NOT NULL PRIMARY KEY,
                        `remote_version` INTEGER NOT NULL,
                        `last_sync_at` TEXT,
                        `has_local_changes` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // ===== SYNC_CONFICTS =====
                // Таблица для SyncConflictEntity, если её ещё нет
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_conflicts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `project_id` TEXT NOT NULL,
                        `entity` TEXT NOT NULL,
                        `entity_id` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}