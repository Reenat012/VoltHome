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
class Migration29To30Test {

    private val databaseName = "migration-29-30.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationPreservesGroupsAndAddsLegacyExplanationMetadata() {
        helper.createDatabase(databaseName, 29).apply {
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
                    "rcd_current,created_at,phase,project_id) " +
                    "VALUES(7,1,1,'Комната','SOCKET',9.5,16,2.5,'C',1,30,1,'A','p1')"
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*migrationsFrom(29))
            .build()
        val db = migrated.openHelper.writableDatabase

        db.query(
            "SELECT group_number,circuit_breaker,rcd_required,rcd_reason_codes," +
                "calculation_source,algorithm_version FROM groups WHERE group_id=7"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertEquals(16, it.getInt(1))
            assertEquals(1, it.getInt(2))
            assertEquals("", it.getString(3))
            assertEquals("LEGACY", it.getString(4))
            assertEquals(0, it.getInt(5))
        }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }

        migrated.close()
    }
}
