package ru.mugalimov.volthome.di.database

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
    db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
        return it.moveToFirst()
    }
}

private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
    var c: Cursor? = null
    return try {
        c = db.query("PRAGMA table_info($table)")
        val nameIx = c.getColumnIndex("name")
        while (c.moveToNext()) {
            if (c.getString(nameIx) == column) return true
        }
        false
    } finally { c?.close() }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1) projects
        if (!tableExists(db, "projects")) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS projects(
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    note TEXT,
                    version INTEGER NOT NULL DEFAULT 1,
                    updated_at TEXT NOT NULL,
                    is_deleted INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_projects_updated_at ON projects(updated_at)")
        }
        // 2) project_local_state
        if (!tableExists(db, "project_local_state")) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS project_local_state(
                    project_id TEXT NOT NULL PRIMARY KEY,
                    remote_version INTEGER NOT NULL DEFAULT 0,
                    last_sync_at TEXT,
                    has_local_changes INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }
        // 3) sync_conflicts
        if (!tableExists(db, "sync_conflicts")) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sync_conflicts(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    project_id TEXT NOT NULL,
                    entity TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_conflicts_proj ON sync_conflicts(project_id)")
        }

        // 4) Расширяем существующие доменные таблицы
        // ПРАВКА: если у вас иные имена таблиц — поправьте ниже.
        addSyncColumns(db, "rooms")
        addSyncColumns(db, "devices")
        addSyncColumns(db, "circuit_groups") // ваша Group-таблица судя по Entity
    }

    private fun addSyncColumns(db: SupportSQLiteDatabase, table: String) {
        if (!tableExists(db, table)) return
        if (!columnExists(db, table, "project_id")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN project_id TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_${table}_project_id ON $table(project_id)")
        }
        if (!columnExists(db, table, "updated_at")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN updated_at TEXT NOT NULL DEFAULT '1970-01-01T00:00:00Z'")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_${table}_proj_updated ON $table(project_id, updated_at)")
        }
        if (!columnExists(db, table, "is_deleted")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_${table}_proj_deleted ON $table(project_id, is_deleted)")
        }
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_projects_is_deleted ON projects(is_deleted)")
    }
}