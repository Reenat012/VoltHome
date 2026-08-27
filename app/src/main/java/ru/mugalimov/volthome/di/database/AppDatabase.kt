package ru.mugalimov.volthome.di.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.mugalimov.volthome.data.local.dao.ApparatusSelectionDao
import ru.mugalimov.volthome.data.local.dao.CableCalculationDao
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.local.dao.ProjectDao
import ru.mugalimov.volthome.data.local.dao.ProjectLocalStateDao
import ru.mugalimov.volthome.data.local.dao.ProjectSetupDao
import ru.mugalimov.volthome.data.local.dao.PanelLayoutDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.dao.RoomsTxDao
import ru.mugalimov.volthome.data.local.entity.ApparatusSelectionEntity
import ru.mugalimov.volthome.data.local.entity.CableLineCalculationEntity
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.data.local.entity.GroupPhaseOverrideEntity
import ru.mugalimov.volthome.data.local.entity.ProjectEntity
import ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity
import ru.mugalimov.volthome.data.local.entity.ProjectSetupEntity
import ru.mugalimov.volthome.data.local.entity.ProjectCableDefaultsEntity
import ru.mugalimov.volthome.data.local.entity.PanelLayoutEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
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
        GroupDeviceJoin::class,
        CircuitGroupEntity::class,

        // под проекты и синк:
        ProjectEntity::class,
        ProjectLocalStateEntity::class,
        GroupPhaseOverrideEntity::class,
        ApparatusSelectionEntity::class,
        PanelLayoutEntity::class,
        ProjectSetupEntity::class,
        ProjectCableDefaultsEntity::class,
        CableLineCalculationEntity::class
    ],
    version = 36,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun roomDao(): RoomDao
    abstract fun deviceDao(): DeviceDao
    abstract fun groupDao(): GroupDao
    abstract fun groupDeviceJoinDao(): GroupDeviceJoinDao
    abstract fun roomsTxDao(): RoomsTxDao

    // проекты / синк
    abstract fun projectDao(): ProjectDao
    abstract fun projectLocalStateDao(): ProjectLocalStateDao
    abstract fun projectSetupDao(): ProjectSetupDao
    abstract fun cableCalculationDao(): CableCalculationDao

    abstract fun groupPhaseOverrideDao(): GroupPhaseOverrideDao
    abstract fun apparatusSelectionDao(): ApparatusSelectionDao
    abstract fun panelLayoutDao(): PanelLayoutDao

    companion object {

        /**
         * Самая ранняя схема, с которой приложение публиковалось в магазине.
         *
         * Версия 1.2 (versionCode 3) уже использовала схему Room 13. Более ранние
         * схемы не должны молча уничтожаться: если в разработческой или сторонней
         * сборке встретится неизвестная версия, Room остановит открытие базы вместо
         * потери пользовательских данных.
         */
        const val MIN_SUPPORTED_DATABASE_VERSION = 13

        // ======== существующие миграции ========

        // ======== missing legacy migrations ========

        // 13 → 14
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // В опубликованной схеме 14 у группы впервые появилась фаза.
                // Старые проекты были однофазными по умолчанию, поэтому A —
                // единственное значение, которое сохраняет прежний смысл данных.
                db.execSQL(
                    "ALTER TABLE groups ADD COLUMN phase TEXT NOT NULL DEFAULT 'A'"
                )
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

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // Таблица новая, но если уже успели создать "кривую" — гасим её.
                db.execSQL("DROP TABLE IF EXISTS group_phase_overrides")

                // Важно: phase = TEXT (Room через TypeConverter будет писать Phase.name)
                // updated_at = INTEGER (epoch millis)
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS group_phase_overrides (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                project_id TEXT NOT NULL,
                group_id INTEGER NOT NULL,
                phase TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(group_id) REFERENCES groups(group_id) ON DELETE CASCADE
            )
            """.trimIndent()
                )

                // Индексы: имена и состав должны совпасть с тем, что ожидает Room по @Entity(indices=...)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_group_phase_overrides_group_id ON group_phase_overrides(group_id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_group_phase_overrides_project_id ON group_phase_overrides(project_id)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_group_phase_overrides_project_id_group_id ON group_phase_overrides(project_id, group_id)"
                )
            }
        }

        /**
         * 26 -> 27: добавляем persisted marker active_manual_project_id в project_local_state.
         * Safe: проверяем PRAGMA table_info, чтобы не падать на "кривых" базах.
         */
        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.query("PRAGMA table_info(project_local_state)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    var exists = false

                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && cursor.getString(nameIndex) == "active_manual_project_id") {
                            exists = true
                            break
                        }
                    }

                    if (!exists) {
                        db.execSQL(
                            """
                        ALTER TABLE project_local_state
                        ADD COLUMN active_manual_project_id TEXT
                        """.trimIndent()
                        )
                    }
                }
            }
        }

        /**
         * 27 -> 28: добавляем persisted ownership поля:
         * - manual_overrides_present (SoT lock)
         * - manual_lock_bootstrap_version (версия bootstrap/backfill)
         *
         * Safe: проверяем PRAGMA table_info, чтобы не падать на "кривых" базах.
         */
        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.query("PRAGMA table_info(project_local_state)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")

                    var hasManualOverrides = false
                    var hasBootstrapVersion = false

                    while (cursor.moveToNext()) {
                        val col = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                        when (col) {
                            "manual_overrides_present" -> hasManualOverrides = true
                            "manual_lock_bootstrap_version" -> hasBootstrapVersion = true
                        }
                    }

                    // ownership lock: INTEGER 0/1 (Room будет маппить в Boolean)
                    if (!hasManualOverrides) {
                        db.execSQL(
                            """
                            ALTER TABLE project_local_state
                            ADD COLUMN manual_overrides_present INTEGER NOT NULL DEFAULT 0
                            """.trimIndent()
                        )
                    }

                    // bootstrap version: INTEGER
                    if (!hasBootstrapVersion) {
                        db.execSQL(
                            """
                            ALTER TABLE project_local_state
                            ADD COLUMN manual_lock_bootstrap_version INTEGER NOT NULL DEFAULT 0
                            """.trimIndent()
                        )
                    }
                }
            }
        }

        /**
         * 28 -> 29: окончательный переход на полностью локальную модель данных.
         *
         * Сохраняем пользовательские проекты и рабочую структуру, физически исключаем
         * ранее удалённые tombstone-записи и удаляем серверные очереди/UUID-карты.
         */
        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite должен переписать ссылки дочерних таблиц при RENAME родителя.
                // На части устройств legacy_alter_table может быть включён глобально.
                db.execSQL("PRAGMA legacy_alter_table=OFF")
                val fallbackProjectId = UUID.randomUUID().toString()
                val nowIso = SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    Locale.US
                ).format(Date())

                db.execSQL(
                    """
                    INSERT INTO projects(id, name, note, version, updated_at, is_deleted)
                    SELECT ?, 'Проект №1', NULL, 0, ?, 0
                    WHERE NOT EXISTS (SELECT 1 FROM projects)
                    """.trimIndent(),
                    arrayOf(fallbackProjectId, nowIso)
                )

                db.execSQL(
                    """
                    CREATE TABLE rooms_v29 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        room_type TEXT NOT NULL,
                        project_id TEXT NOT NULL,
                        FOREIGN KEY(project_id) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO rooms_v29(id,name,created_at,room_type,project_id)
                    SELECT r.id,r.name,r.created_at,r.room_type,
                           COALESCE((SELECT p.id FROM projects p WHERE p.id=r.project_id LIMIT 1),
                                    (SELECT id FROM projects ORDER BY rowid LIMIT 1))
                    FROM rooms r
                    WHERE NOT EXISTS (
                        SELECT 1 FROM tombstones t
                        WHERE t.entity_type='ROOM' AND t.local_id=r.id
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE devices_v29 (
                        device_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        power INTEGER NOT NULL,
                        voltage TEXT NOT NULL,
                        demand_ratio REAL NOT NULL,
                        created_at INTEGER NOT NULL,
                        room_id INTEGER,
                        device_type TEXT NOT NULL,
                        power_factor REAL NOT NULL,
                        has_motor INTEGER NOT NULL DEFAULT 0,
                        requires_dedicated INTEGER NOT NULL DEFAULT 0,
                        requires_socket INTEGER NOT NULL DEFAULT 1,
                        project_id TEXT NOT NULL,
                        FOREIGN KEY(room_id) REFERENCES rooms_v29(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(project_id) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO devices_v29(
                        device_id,name,power,voltage,demand_ratio,created_at,room_id,device_type,
                        power_factor,has_motor,requires_dedicated,requires_socket,project_id
                    )
                    SELECT d.device_id,d.name,d.power,d.voltage,d.demand_ratio,d.created_at,
                           CASE WHEN r.id IS NULL THEN NULL ELSE d.room_id END,
                           d.device_type,d.power_factor,d.has_motor,d.requires_dedicated,d.requires_socket,
                           COALESCE(r.project_id,
                                    (SELECT p.id FROM projects p WHERE p.id=d.project_id LIMIT 1),
                                    (SELECT id FROM projects ORDER BY rowid LIMIT 1))
                    FROM devices d
                    LEFT JOIN rooms_v29 r ON r.id=d.room_id
                    WHERE NOT EXISTS (
                        SELECT 1 FROM tombstones t
                        WHERE t.entity_type='DEVICE' AND t.local_id=d.device_id
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE groups_v29 (
                        group_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        group_number INTEGER NOT NULL,
                        room_id INTEGER NOT NULL,
                        room_name TEXT NOT NULL,
                        group_type TEXT NOT NULL,
                        nominal_current REAL NOT NULL,
                        circuit_breaker INTEGER NOT NULL,
                        cable_section REAL NOT NULL,
                        breaker_type TEXT NOT NULL,
                        rcd_required INTEGER NOT NULL,
                        rcd_current INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        phase TEXT NOT NULL,
                        project_id TEXT NOT NULL,
                        FOREIGN KEY(room_id) REFERENCES rooms_v29(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(project_id) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO groups_v29(
                        group_id,group_number,room_id,room_name,group_type,nominal_current,
                        circuit_breaker,cable_section,breaker_type,rcd_required,rcd_current,
                        created_at,phase,project_id
                    )
                    SELECT g.group_id,g.group_number,g.room_id,g.room_name,g.group_type,g.nominal_current,
                           g.circuit_breaker,g.cable_section,g.breaker_type,g.rcd_required,g.rcd_current,
                           g.created_at,g.phase,r.project_id
                    FROM groups g
                    JOIN rooms_v29 r ON r.id=g.room_id
                    WHERE NOT EXISTS (
                        SELECT 1 FROM tombstones t
                        WHERE t.entity_type='GROUP' AND t.local_id=g.group_id
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE project_local_state_v29 (
                        project_id TEXT NOT NULL,
                        active_manual_project_id TEXT,
                        manual_overrides_present INTEGER NOT NULL,
                        manual_lock_bootstrap_version INTEGER NOT NULL,
                        PRIMARY KEY(project_id),
                        FOREIGN KEY(project_id) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO project_local_state_v29(
                        project_id,active_manual_project_id,manual_overrides_present,manual_lock_bootstrap_version
                    )
                    SELECT p.id,s.active_manual_project_id,
                           COALESCE(s.manual_overrides_present,0),
                           COALESCE(s.manual_lock_bootstrap_version,0)
                    FROM projects p
                    LEFT JOIN project_local_state s ON s.project_id=p.id
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE group_device_join_v29 (
                        group_id INTEGER NOT NULL,
                        device_id INTEGER NOT NULL,
                        PRIMARY KEY(group_id,device_id),
                        FOREIGN KEY(group_id) REFERENCES groups_v29(group_id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(device_id) REFERENCES devices_v29(device_id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO group_device_join_v29(group_id,device_id)
                    SELECT j.group_id,j.device_id
                    FROM group_device_join j
                    JOIN groups_v29 g ON g.group_id=j.group_id
                    JOIN devices_v29 d ON d.device_id=j.device_id AND d.project_id=g.project_id
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE group_phase_overrides_v29 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        project_id TEXT NOT NULL,
                        group_id INTEGER NOT NULL,
                        phase TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(group_id) REFERENCES groups_v29(group_id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(project_id) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO group_phase_overrides_v29(id,project_id,group_id,phase,updated_at)
                    SELECT o.id,g.project_id,o.group_id,o.phase,o.updated_at
                    FROM group_phase_overrides o JOIN groups_v29 g ON g.group_id=o.group_id
                    """.trimIndent()
                )

                listOf(
                    "group_device_join", "group_phase_overrides", "loads", "devices", "groups", "rooms",
                    "project_local_state", "outbox", "tombstones", "sync_conflicts",
                    "uuid_map_rooms", "uuid_map_groups", "uuid_map_devices"
                ).forEach { table -> db.execSQL("DROP TABLE IF EXISTS `$table`") }

                db.execSQL("ALTER TABLE rooms_v29 RENAME TO rooms")
                db.execSQL("ALTER TABLE devices_v29 RENAME TO devices")
                db.execSQL("ALTER TABLE groups_v29 RENAME TO groups")
                db.execSQL("ALTER TABLE project_local_state_v29 RENAME TO project_local_state")
                db.execSQL("ALTER TABLE group_device_join_v29 RENAME TO group_device_join")
                db.execSQL("ALTER TABLE group_phase_overrides_v29 RENAME TO group_phase_overrides")

                db.execSQL("CREATE UNIQUE INDEX uq_rooms_name_project ON rooms(name,project_id)")
                db.execSQL("CREATE INDEX idx_rooms_project_id ON rooms(project_id)")
                db.execSQL("CREATE INDEX idx_rooms_project_created_id ON rooms(project_id,created_at,id)")
                db.execSQL("CREATE INDEX idx_devices_room_id ON devices(room_id)")
                db.execSQL("CREATE INDEX idx_devices_project_id ON devices(project_id)")
                db.execSQL("CREATE INDEX idx_devices_name ON devices(name)")
                db.execSQL("CREATE INDEX idx_devices_project_room_created_id ON devices(project_id,room_id,created_at,device_id)")
                db.execSQL("CREATE INDEX idx_groups_room_id ON groups(room_id)")
                db.execSQL("CREATE INDEX idx_groups_project_id ON groups(project_id)")
                db.execSQL("CREATE INDEX idx_groups_project_room_id ON groups(project_id,room_id)")
                db.execSQL("CREATE INDEX idx_group_device_join_device_id ON group_device_join(device_id)")
                db.execSQL("CREATE INDEX index_group_phase_overrides_group_id ON group_phase_overrides(group_id)")
                db.execSQL("CREATE INDEX index_group_phase_overrides_project_id ON group_phase_overrides(project_id)")
                db.execSQL("CREATE UNIQUE INDEX index_group_phase_overrides_project_id_group_id ON group_phase_overrides(project_id,group_id)")
            }
        }

        /**
         * 29 -> 30: метаданные прозрачности расчёта.
         * Результаты и пользовательская структура групп не изменяются.
         */
        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE groups ADD COLUMN rcd_reason_codes TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE groups ADD COLUMN calculation_source TEXT NOT NULL DEFAULT 'LEGACY'"
                )
                db.execSQL(
                    "ALTER TABLE groups ADD COLUMN algorithm_version INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 30 -> 31: исправление правила дифференциальной защиты.
         *
         * Существующие группы не пересобираются и устройства между ними не перемещаются.
         * Если группа содержит бытовую розетку либо нагрузку, подключаемую через розетку,
         * этой фактической групповой линии назначается УЗО 30 мА.
         */
        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE groups
                    SET rcd_required = 1,
                        rcd_current = 30,
                        rcd_reason_codes = CASE
                            WHEN rcd_reason_codes = '' THEN 'GENERAL_PURPOSE_SOCKET'
                            WHEN instr(
                                ',' || rcd_reason_codes || ',',
                                ',GENERAL_PURPOSE_SOCKET,'
                            ) = 0
                                THEN rcd_reason_codes || ',GENERAL_PURPOSE_SOCKET'
                            ELSE rcd_reason_codes
                        END,
                        algorithm_version = 3
                    WHERE EXISTS (
                        SELECT 1
                        FROM group_device_join AS link
                        JOIN devices AS device ON device.device_id = link.device_id
                        WHERE link.group_id = groups.group_id
                          AND device.device_type = 'SOCKET'
                          AND device.requires_socket = 0
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    UPDATE groups
                    SET rcd_required = 1,
                        rcd_current = 30,
                        rcd_reason_codes = CASE
                            WHEN rcd_reason_codes = '' THEN 'SOCKET_CONNECTED_LOAD'
                            WHEN instr(
                                ',' || rcd_reason_codes || ',',
                                ',SOCKET_CONNECTED_LOAD,'
                            ) = 0
                                THEN rcd_reason_codes || ',SOCKET_CONNECTED_LOAD'
                            ELSE rcd_reason_codes
                        END,
                        algorithm_version = 3
                    WHERE EXISTS (
                        SELECT 1
                        FROM group_device_join AS link
                        JOIN devices AS device ON device.device_id = link.device_id
                        WHERE link.group_id = groups.group_id
                          AND device.requires_socket = 1
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * 31 -> 32: полная спецификация групповой дифференциальной защиты.
         *
         * Старые решения не переоцениваются: чувствительность сохраняется,
         * недостающий номинальный ток остаётся NULL, источник помечается LEGACY.
         */
        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE groups ADD COLUMN rcd_nominal_current INTEGER")
                db.execSQL("ALTER TABLE groups ADD COLUMN rcd_type TEXT")
                db.execSQL("ALTER TABLE groups ADD COLUMN rcd_poles INTEGER")
                db.execSQL(
                    "ALTER TABLE groups ADD COLUMN rcd_selectivity TEXT NOT NULL DEFAULT 'NONE'"
                )
                db.execSQL("ALTER TABLE groups ADD COLUMN rcd_kind TEXT")
                db.execSQL(
                    "ALTER TABLE groups ADD COLUMN rcd_source TEXT NOT NULL DEFAULT 'LEGACY'"
                )
                db.execSQL(
                    "ALTER TABLE groups ADD COLUMN manual_deviation_codes TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    UPDATE groups
                    SET rcd_type = CASE WHEN rcd_required = 1 THEN 'A' ELSE NULL END,
                        rcd_poles = CASE
                            WHEN rcd_required = 0 THEN NULL
                            WHEN phase = 'THREE_PHASE' THEN 4
                            ELSE 2
                        END,
                        rcd_kind = CASE WHEN rcd_required = 1 THEN 'RCD' ELSE NULL END,
                        rcd_source = 'LEGACY'
                    """.trimIndent()
                )
            }
        }

        /** 32 -> 33: локальные проектные снимки выбранных моделей аппаратов. */
        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `apparatus_selections` (
                        `project_id` TEXT NOT NULL,
                        `slot_id` TEXT NOT NULL,
                        `snapshot_json` TEXT NOT NULL,
                        `catalog_version` TEXT NOT NULL,
                        `updated_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`project_id`, `slot_id`),
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_apparatus_selections_project_id` " +
                        "ON `apparatus_selections`(`project_id`)"
                )
            }
        }

        /** 33 -> 34: сохранённая пользовательская DIN-компоновка проекта. */
        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `panel_layouts` (
                        `project_id` TEXT NOT NULL,
                        `snapshot_json` TEXT NOT NULL,
                        `schema_version` INTEGER NOT NULL,
                        `updated_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`project_id`),
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_panel_layouts_project_id` " +
                        "ON `panel_layouts`(`project_id`)"
                )
            }
        }

        /** 34 -> 35: параметры проекта, выбранные в мастере создания. */
        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `project_setup` (
                        `project_id` TEXT NOT NULL,
                        `object_type` TEXT NOT NULL,
                        `phase_mode` TEXT NOT NULL,
                        `input_power_kw` REAL,
                        `source_template_id` TEXT,
                        `source_template_version` INTEGER NOT NULL,
                        `wizard_completed` INTEGER NOT NULL,
                        PRIMARY KEY(`project_id`),
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_setup_project_id` " +
                        "ON `project_setup`(`project_id`)"
                )
            }
        }

        /** 35 -> 36: условия и воспроизводимые результаты расчёта кабельных линий. */
        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `project_cable_defaults` (
                        `project_id` TEXT NOT NULL,
                        `material` TEXT NOT NULL,
                        `insulation` TEXT NOT NULL,
                        `installation_method` TEXT NOT NULL,
                        `ambient_temperature_c` INTEGER NOT NULL,
                        `grouped_circuits` INTEGER NOT NULL,
                        `max_voltage_drop_percent` REAL NOT NULL,
                        PRIMARY KEY(`project_id`),
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_cable_defaults_project_id` " +
                        "ON `project_cable_defaults`(`project_id`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cable_line_calculations` (
                        `group_id` INTEGER NOT NULL,
                        `project_id` TEXT NOT NULL,
                        `phase_mode` TEXT NOT NULL,
                        `load_current_a` REAL NOT NULL,
                        `breaker_a` INTEGER NOT NULL,
                        `length_m` REAL,
                        `power_factor` REAL NOT NULL,
                        `material` TEXT NOT NULL,
                        `insulation` TEXT NOT NULL,
                        `installation_method` TEXT NOT NULL,
                        `ambient_temperature_c` INTEGER NOT NULL,
                        `grouped_circuits` INTEGER NOT NULL,
                        `max_voltage_drop_percent` REAL NOT NULL,
                        `manual_section_mm2` REAL,
                        `phase_section_mm2` REAL NOT NULL,
                        `neutral_section_mm2` REAL NOT NULL,
                        `pe_section_mm2` REAL NOT NULL,
                        `cores` INTEGER NOT NULL,
                        `base_ampacity_a` REAL NOT NULL,
                        `installation_factor` REAL NOT NULL,
                        `temperature_factor` REAL NOT NULL,
                        `grouping_factor` REAL NOT NULL,
                        `corrected_ampacity_a` REAL NOT NULL,
                        `voltage_drop_v` REAL,
                        `voltage_drop_percent` REAL,
                        `status` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `checks` TEXT NOT NULL,
                        `algorithm_version` INTEGER NOT NULL,
                        `dataset_version` TEXT NOT NULL,
                        `updated_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`group_id`),
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`group_id`) REFERENCES `groups`(`group_id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cable_line_calculations_project_id` " +
                        "ON `cable_line_calculations`(`project_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cable_line_calculations_group_id` " +
                        "ON `cable_line_calculations`(`group_id`)"
                )
            }
        }

        /**
         * Единый реестр миграций. И production-конфигурация, и тесты используют
         * один и тот же список, поэтому новую миграцию нельзя случайно добавить
         * только в одно из этих мест.
         */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36
        )

        fun migrationsFrom(version: Int): Array<Migration> =
            ALL_MIGRATIONS
                .filter { migration -> migration.startVersion >= version }
                .toTypedArray()
    }
}
