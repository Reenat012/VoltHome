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
class Migration34To35Test {
    private val databaseName = "migration-34-35.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationPreservesProjectsAndAddsProjectScopedSetup() {
        helper.createDatabase(databaseName, 34).apply {
            execSQL(
                "INSERT INTO projects(id,name,note,version,updated_at,is_deleted) " +
                    "VALUES('p1','Дом',NULL,0,'2026-08-24T00:00:00.000Z',0)"
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*migrationsFrom(34))
            .build()
        val db = migrated.openHelper.writableDatabase

        db.query("SELECT name FROM projects WHERE id='p1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Дом", it.getString(0))
        }

        db.execSQL(
            "INSERT INTO project_setup(" +
                "project_id,object_type,phase_mode,input_power_kw,source_template_id," +
                "source_template_version,wizard_completed" +
                ") VALUES('p1','HOUSE','THREE',15.0,'house_v1',1,1)"
        )
        db.query("SELECT phase_mode,input_power_kw FROM project_setup WHERE project_id='p1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("THREE", it.getString(0))
            assertEquals(15.0, it.getDouble(1), 0.0)
        }

        db.execSQL("DELETE FROM projects WHERE id='p1'")
        db.query("SELECT 1 FROM project_setup WHERE project_id='p1'").use {
            assertFalse(it.moveToFirst())
        }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        migrated.close()
    }
}
