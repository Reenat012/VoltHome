package ru.mugalimov.volthome.di.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration33To34Test {
    private val databaseName = "migration-33-34.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationCreatesProjectScopedPanelLayoutWithCascadeDelete() {
        helper.createDatabase(databaseName, 33).apply {
            execSQL(
                "INSERT INTO projects(id,name,note,version,updated_at,is_deleted) " +
                    "VALUES('p1','Дом',NULL,0,'2026-08-10T00:00:00.000Z',0)"
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*migrationsFrom(33))
            .build()
        val db = migrated.openHelper.writableDatabase

        db.execSQL(
            "INSERT INTO panel_layouts(" +
                "project_id,snapshot_json,schema_version,updated_at_epoch_ms" +
                ") VALUES('p1','{}',1,1)"
        )
        db.query("SELECT 1 FROM panel_layouts WHERE project_id='p1'").use {
            assertTrue(it.moveToFirst())
        }

        db.execSQL("DELETE FROM projects WHERE id='p1'")
        db.query("SELECT 1 FROM panel_layouts WHERE project_id='p1'").use {
            assertFalse(it.moveToFirst())
        }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        migrated.close()
    }
}
