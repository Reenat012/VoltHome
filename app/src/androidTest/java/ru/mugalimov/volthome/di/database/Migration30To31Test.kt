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
class Migration30To31Test {

    private val databaseName = "migration-30-31.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationAddsRcdToSocketAndSocketConnectedGroupsWithoutRegrouping() {
        helper.createDatabase(databaseName, 30).apply {
            execSQL(
                "INSERT INTO projects(id,name,note,version,updated_at,is_deleted) " +
                    "VALUES('p1','Дом',NULL,0,'2026-07-23T00:00:00.000Z',0)"
            )
            execSQL(
                "INSERT INTO rooms(id,name,created_at,room_type,project_id) " +
                    "VALUES(1,'Комната',1,'STANDARD','p1')"
            )
            execSQL(
                "INSERT INTO devices(device_id,name,power,voltage,demand_ratio,created_at," +
                    "room_id,device_type,power_factor,has_motor,requires_dedicated," +
                    "requires_socket,project_id) VALUES" +
                    "(1,'Микроволновая печь',1000,'230|AC_1PHASE',0.8,1,1,'SOCKET',0.95,0,0,1,'p1')," +
                    "(2,'Розетка бытовая',2200,'230|AC_1PHASE',0.75,1,1,'SOCKET',0.95,0,0,0,'p1')"
            )
            execSQL(
                "INSERT INTO groups(group_id,group_number,room_id,room_name,group_type," +
                    "nominal_current,circuit_breaker,cable_section,breaker_type,rcd_required," +
                    "rcd_current,rcd_reason_codes,calculation_source,algorithm_version," +
                    "created_at,phase,project_id) VALUES" +
                    "(10,1,1,'Комната','OTHER',4.6,16,2.5,'C',0,30,'','AUTO',2,1,'A','p1')," +
                    "(11,2,1,'Комната','SOCKET',9.5,16,2.5,'C',0,30,'','AUTO',2,1,'A','p1')"
            )
            execSQL(
                "INSERT INTO group_device_join(group_id,device_id) VALUES(10,1),(11,2)"
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*migrationsFrom(30))
            .build()
        val db = migrated.openHelper.writableDatabase

        db.query(
            "SELECT group_id,rcd_required,rcd_current,rcd_reason_codes,algorithm_version " +
                "FROM groups ORDER BY group_id"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(10, it.getInt(0))
            assertEquals(1, it.getInt(1))
            assertEquals(30, it.getInt(2))
            assertEquals("SOCKET_CONNECTED_LOAD", it.getString(3))
            assertEquals(3, it.getInt(4))

            assertTrue(it.moveToNext())
            assertEquals(11, it.getInt(0))
            assertEquals(1, it.getInt(1))
            assertEquals(30, it.getInt(2))
            assertEquals("GENERAL_PURPOSE_SOCKET", it.getString(3))
            assertEquals(3, it.getInt(4))

            assertFalse(it.moveToNext())
        }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }

        migrated.close()
    }
}
