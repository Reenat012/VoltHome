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
class Migration35To36Test {
    private val databaseName = "migration-35-36.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationAddsProjectDefaultsAndCableLineResultsWithCascade() {
        helper.createDatabase(databaseName, 35).apply {
            execSQL(
                "INSERT INTO projects(id,name,note,version,updated_at,is_deleted) " +
                    "VALUES('p1','Дом',NULL,0,'2026-08-25T00:00:00.000Z',0)"
            )
            execSQL(
                "INSERT INTO rooms(id,name,created_at,room_type,project_id) " +
                    "VALUES(1,'Кухня',0,'KITCHEN','p1')"
            )
            execSQL(
                "INSERT INTO groups(" +
                    "group_id,group_number,room_id,room_name,group_type,nominal_current," +
                    "circuit_breaker,cable_section,breaker_type,rcd_required,rcd_current," +
                    "rcd_reason_codes,rcd_nominal_current,rcd_type,rcd_poles,rcd_selectivity," +
                    "rcd_kind,rcd_source,manual_deviation_codes,calculation_source," +
                    "algorithm_version,created_at,phase,project_id" +
                    ") VALUES(1,1,1,'Кухня','SOCKET',8.4,16,2.5,'C',1,30," +
                    "'[]',25,'A',2,'NONE','RCD','AUTO','[]','AUTO',2,0,'A','p1')"
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_35_36)
            .build()
        val db = migrated.openHelper.writableDatabase

        db.execSQL(
            "INSERT INTO project_cable_defaults VALUES(" +
                "'p1','COPPER','PVC_70','CONDUIT_WALL',25,1,3.0)"
        )
        db.execSQL(
            "INSERT INTO cable_line_calculations VALUES(" +
                "1,'p1','SINGLE',8.4,16,20.0,0.95,'COPPER','PVC_70','CONDUIT_WALL'," +
                "25,1,3.0,NULL,2.5,2.5,2.5,3,25.0,0.8,1.06,1.0,21.2,2.0,0.87," +
                "'PASSED','AUTOMATIC','[]',1,'VH-CABLE-2026.1',0)"
        )

        db.query("SELECT cores,phase_section_mm2 FROM cable_line_calculations WHERE group_id=1").use {
            assertTrue(it.moveToFirst())
            assertEquals(3, it.getInt(0))
            assertEquals(2.5, it.getDouble(1), 0.0)
        }
        db.execSQL("DELETE FROM projects WHERE id='p1'")
        db.query("SELECT 1 FROM project_cable_defaults WHERE project_id='p1'").use {
            assertFalse(it.moveToFirst())
        }
        db.query("SELECT 1 FROM cable_line_calculations WHERE project_id='p1'").use {
            assertFalse(it.moveToFirst())
        }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        migrated.close()
    }
}
