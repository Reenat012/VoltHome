package ru.mugalimov.volthome.di.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration28To29Test {

    private val databaseName = "migration-28-29.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationPreservesLocalDataAndRemovesSyncTables() {
        helper.createDatabase(databaseName, 28).apply {
            execSQL(
                "INSERT INTO projects(id,name,note,version,updated_at,is_deleted) " +
                    "VALUES('p1','Дом',NULL,0,'2026-07-22T00:00:00.000Z',0)"
            )
            execSQL(
                "INSERT INTO rooms(id,name,created_at,room_type,project_id) " +
                    "VALUES(1,'Кухня',1,'KITCHEN','p1')"
            )
            execSQL(
                "INSERT INTO devices(device_id,name,power,voltage,demand_ratio,created_at,room_id," +
                    "device_type,power_factor,has_motor,requires_dedicated,requires_socket,project_id) " +
                    "VALUES(10,'Розетка',2200,'V220',1.0,1,1,'SOCKET',0.95,0,0,1,'p1')"
            )
            execSQL(
                "INSERT INTO devices(device_id,name,power,voltage,demand_ratio,created_at,room_id," +
                    "device_type,power_factor,has_motor,requires_dedicated,requires_socket,project_id) " +
                    "VALUES(11,'Удалённое',10,'V220',1.0,2,1,'LIGHTING',1.0,0,0,1,'p1')"
            )
            execSQL(
                "INSERT INTO tombstones(project_id,entity_type,local_id,server_uuid,created_at) " +
                    "VALUES('p1','DEVICE',11,NULL,3)"
            )
            execSQL(
                "INSERT INTO project_local_state(project_id,remote_version,last_sync_at,has_local_changes," +
                    "active_manual_project_id,manual_overrides_present,manual_lock_bootstrap_version) " +
                    "VALUES('p1',7,'old-server-time',1,NULL,1,3)"
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*migrationsFrom(28))
            .build()
        val db = migrated.openHelper.writableDatabase

        db.query("SELECT COUNT(*) FROM rooms WHERE project_id='p1'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM devices WHERE project_id='p1'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT manual_overrides_present,manual_lock_bootstrap_version FROM project_local_state WHERE project_id='p1'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertEquals(3, it.getInt(1))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='outbox'").use {
            assertFalse(it.moveToFirst())
        }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }

        db.execSQL("DELETE FROM rooms WHERE id=1")
        db.query("SELECT COUNT(*) FROM devices WHERE device_id=10").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        migrated.close()
    }
}
