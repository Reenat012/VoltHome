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
class Migration31To32Test {

    private val databaseName = "migration-31-32.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationPreservesLegacyRcdAndAddsExplicitSpecification() {
        helper.createDatabase(databaseName, 31).apply {
            execSQL(
                "INSERT INTO projects(id,name,note,version,updated_at,is_deleted) " +
                    "VALUES('p1','Дом',NULL,0,'2026-07-23T00:00:00.000Z',0)"
            )
            execSQL(
                "INSERT INTO rooms(id,name,created_at,room_type,project_id) " +
                    "VALUES(1,'Комната',1,'STANDARD','p1')"
            )
            execSQL(
                "INSERT INTO groups(group_id,group_number,room_id,room_name,group_type," +
                    "nominal_current,circuit_breaker,cable_section,breaker_type,rcd_required," +
                    "rcd_current,rcd_reason_codes,calculation_source,algorithm_version," +
                    "created_at,phase,project_id) VALUES" +
                    "(10,1,1,'Комната','SOCKET',9.5,16,2.5,'C',1,30," +
                    "'GENERAL_PURPOSE_SOCKET','AUTO',3,1,'A','p1')," +
                    "(11,2,1,'Комната','LIGHTING',2.0,10,1.5,'B',0,30," +
                    "'','AUTO',3,1,'THREE_PHASE','p1')"
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*migrationsFrom(31))
            .build()
        val db = migrated.openHelper.writableDatabase

        db.query(
            "SELECT group_id,rcd_nominal_current,rcd_type,rcd_poles," +
                "rcd_selectivity,rcd_kind,rcd_source,manual_deviation_codes " +
                "FROM groups ORDER BY group_id"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(10, it.getInt(0))
            assertTrue(it.isNull(1))
            assertEquals("A", it.getString(2))
            assertEquals(2, it.getInt(3))
            assertEquals("NONE", it.getString(4))
            assertEquals("RCD", it.getString(5))
            assertEquals("LEGACY", it.getString(6))
            assertEquals("", it.getString(7))

            assertTrue(it.moveToNext())
            assertEquals(11, it.getInt(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
            assertTrue(it.isNull(3))
            assertTrue(it.isNull(5))
            assertEquals("", it.getString(7))
            assertFalse(it.moveToNext())
        }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        migrated.close()
    }
}
