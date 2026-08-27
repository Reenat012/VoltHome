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
class Migration32To33Test {

    private val databaseName = "migration-32-33.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationCreatesProjectScopedSelectionStorageWithCascadeDelete() {
        helper.createDatabase(databaseName, 32).apply {
            execSQL(
                "INSERT INTO projects(id,name,note,version,updated_at,is_deleted) " +
                    "VALUES('p1','Дом',NULL,0,'2026-08-07T00:00:00.000Z',0)"
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*migrationsFrom(32))
            .build()
        val db = migrated.openHelper.writableDatabase

        db.execSQL(
            "INSERT INTO apparatus_selections(" +
                "project_id,slot_id,snapshot_json,catalog_version,updated_at_epoch_ms" +
                ") VALUES('p1','incomer-breaker','{}','v1',1)"
        )
        db.query("SELECT 1 FROM apparatus_selections WHERE project_id='p1'").use {
            assertTrue(it.moveToFirst())
        }

        db.execSQL("DELETE FROM projects WHERE id='p1'")
        db.query("SELECT 1 FROM apparatus_selections WHERE project_id='p1'").use {
            assertFalse(it.moveToFirst())
        }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        migrated.close()
    }
}
