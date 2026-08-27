package ru.mugalimov.volthome.di.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверяет обновление с самой ранней схемы, опубликованной в RuStore
 * (ВольтХом 1.2, Room 13), до текущей без потери проекта.
 *
 * Для схем 13–27 JSON-снимки Room исторически не сохранялись, поэтому исходная
 * база создаётся по DDL опубликованной версии 1.2 из истории репозитория.
 */
@RunWith(AndroidJUnit4::class)
class Migration13To36Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-13-current.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun publishedVersion13PreservesProjectDataThroughFullMigrationChain() {
        createPublishedVersion13Database()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*AppDatabase.migrationsFrom(AppDatabase.MIN_SUPPORTED_DATABASE_VERSION))
            .build()
        val db = migrated.openHelper.writableDatabase

        assertEquals(1, db.singleInt("SELECT COUNT(*) FROM projects"))
        assertEquals(1, db.singleInt("SELECT COUNT(*) FROM rooms WHERE id=1"))
        assertEquals(1, db.singleInt("SELECT COUNT(*) FROM devices WHERE device_id=10"))
        assertEquals(1, db.singleInt("SELECT COUNT(*) FROM groups WHERE group_id=20"))
        assertEquals(
            1,
            db.singleInt(
                "SELECT COUNT(*) FROM group_device_join WHERE group_id=20 AND device_id=10"
            )
        )
        assertEquals("Кухня", db.singleString("SELECT name FROM rooms WHERE id=1"))
        assertEquals("Розетка бытовая", db.singleString("SELECT name FROM devices WHERE device_id=10"))

        val projectId = db.singleString("SELECT project_id FROM rooms WHERE id=1")
        assertTrue(projectId.isNotBlank())
        assertEquals(
            projectId,
            db.singleString("SELECT project_id FROM devices WHERE device_id=10")
        )
        assertEquals(
            projectId,
            db.singleString("SELECT project_id FROM groups WHERE group_id=20")
        )

        db.query("PRAGMA foreign_key_check").use { cursor ->
            assertFalse("После миграции нарушены внешние ключи", cursor.moveToFirst())
        }
        migrated.close()
    }

    @Test
    fun productionMigrationRegistryIsContinuousFromPublishedMinimum() {
        var expectedStart = AppDatabase.MIN_SUPPORTED_DATABASE_VERSION
        AppDatabase.ALL_MIGRATIONS.forEach { migration ->
            assertEquals(expectedStart, migration.startVersion)
            assertEquals(expectedStart + 1, migration.endVersion)
            expectedStart = migration.endVersion
        }
        assertEquals(36, expectedStart)
    }

    private fun createPublishedVersion13Database() {
        context.deleteDatabase(databaseName)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(
                    AppDatabase.MIN_SUPPORTED_DATABASE_VERSION
                ) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion13Schema(db)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }
            )
            .build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO rooms(id,name,created_at,room_type) " +
                    "VALUES(1,'Кухня',1000,'KITCHEN')"
            )
            db.execSQL(
                "INSERT INTO devices(device_id,name,power,voltage,demand_ratio,created_at," +
                    "room_id,device_type,power_factor,has_motor,requires_dedicated,requires_socket) " +
                    "VALUES(10,'Розетка бытовая',2200,'V220',0.75,1001,1,'SOCKET',0.95,0,0,1)"
            )
            db.execSQL(
                "INSERT INTO groups(group_id,group_number,room_id,room_name,group_type," +
                    "nominal_current,circuit_breaker,cable_section,breaker_type,rcd_required," +
                    "rcd_current,created_at) " +
                    "VALUES(20,1,1,'Кухня','SOCKET',8.44,16,2.5,'C',1,30,1002)"
            )
            db.execSQL("INSERT INTO group_device_join(group_id,device_id) VALUES(20,10)")
            db.execSQL(
                "INSERT INTO loads(id,name,current,sum_power,count_devices,created_at,room_id) " +
                    "VALUES(30,'Кухня',8.44,2200,1,1003,1)"
            )
        }
    }

    private fun createVersion13Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rooms (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                room_type TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS devices (
                device_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                power INTEGER NOT NULL,
                voltage TEXT NOT NULL,
                demand_ratio REAL NOT NULL,
                created_at INTEGER NOT NULL,
                room_id INTEGER NOT NULL,
                device_type TEXT NOT NULL,
                power_factor REAL NOT NULL,
                has_motor INTEGER NOT NULL,
                requires_dedicated INTEGER NOT NULL,
                requires_socket INTEGER NOT NULL,
                FOREIGN KEY(room_id) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS loads (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                current REAL NOT NULL,
                sum_power INTEGER NOT NULL,
                count_devices INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                room_id INTEGER NOT NULL,
                FOREIGN KEY(room_id) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS groups (
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
                FOREIGN KEY(room_id) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_groups_room_id ON groups(room_id)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS group_device_join (
                group_id INTEGER NOT NULL,
                device_id INTEGER NOT NULL,
                PRIMARY KEY(group_id, device_id),
                FOREIGN KEY(group_id) REFERENCES groups(group_id)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(device_id) REFERENCES devices(device_id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.singleInt(sql: String): Int =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.singleString(sql: String): String =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
}
