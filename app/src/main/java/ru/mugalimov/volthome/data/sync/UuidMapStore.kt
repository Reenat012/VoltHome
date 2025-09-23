package ru.mugalimov.volthome.data.sync

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Таблицы соответствий UUID (сервер) ↔ локальные PK (Room Long), создаются «мягко» (IF NOT EXISTS),
 * не требуют поднятия версии Room:
 *
 *  - uuid_map_rooms(room_uuid TEXT PRIMARY KEY,  local_id INTEGER NOT NULL UNIQUE)
 *  - uuid_map_groups(group_uuid TEXT PRIMARY KEY, local_id INTEGER NOT NULL UNIQUE)
 *  - uuid_map_devices(device_uuid TEXT PRIMARY KEY, local_id INTEGER NOT NULL UNIQUE)
 */
class UuidMapStore(
    private val dbProvider: () -> RoomDatabase
) {
    private val sdb: SupportSQLiteDatabase get() = dbProvider().openHelper.writableDatabase
    @Volatile private var ready = false

    private fun ensure() {
        if (ready) return
        sdb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS uuid_map_rooms(
                room_uuid TEXT PRIMARY KEY,
                local_id  INTEGER NOT NULL UNIQUE
            )
            """.trimIndent()
        )
        sdb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS uuid_map_groups(
                group_uuid TEXT PRIMARY KEY,
                local_id   INTEGER NOT NULL UNIQUE
            )
            """.trimIndent()
        )
        sdb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS uuid_map_devices(
                device_uuid TEXT PRIMARY KEY,
                local_id    INTEGER NOT NULL UNIQUE
            )
            """.trimIndent()
        )
        ready = true
    }

    // ---------- forward lookups ----------
    suspend fun getLocalRoomId(roomUuid: String): Long? = withContext(Dispatchers.IO) {
        ensure()
        sdb.query("SELECT local_id FROM uuid_map_rooms WHERE room_uuid=?", arrayOf(roomUuid)).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }
    suspend fun getLocalGroupId(groupUuid: String): Long? = withContext(Dispatchers.IO) {
        ensure()
        sdb.query("SELECT local_id FROM uuid_map_groups WHERE group_uuid=?", arrayOf(groupUuid)).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }
    suspend fun getLocalDeviceId(deviceUuid: String): Long? = withContext(Dispatchers.IO) {
        ensure()
        sdb.query("SELECT local_id FROM uuid_map_devices WHERE device_uuid=?", arrayOf(deviceUuid)).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    // ---------- reverse lookups ----------
    suspend fun getRoomUuid(localId: Long): String? = withContext(Dispatchers.IO) {
        ensure()
        sdb.query("SELECT room_uuid FROM uuid_map_rooms WHERE local_id=?", arrayOf(localId)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }
    suspend fun getGroupUuid(localId: Long): String? = withContext(Dispatchers.IO) {
        ensure()
        sdb.query("SELECT group_uuid FROM uuid_map_groups WHERE local_id=?", arrayOf(localId)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }
    suspend fun getDeviceUuid(localId: Long): String? = withContext(Dispatchers.IO) {
        ensure()
        sdb.query("SELECT device_uuid FROM uuid_map_devices WHERE local_id=?", arrayOf(localId)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    // ---------- upserts ----------
    suspend fun putRoom(roomUuid: String, localId: Long) = withContext(Dispatchers.IO) {
        ensure()
        sdb.execSQL("INSERT OR IGNORE INTO uuid_map_rooms(room_uuid, local_id) VALUES (?,?)", arrayOf(roomUuid, localId))
    }
    suspend fun putGroup(groupUuid: String, localId: Long) = withContext(Dispatchers.IO) {
        ensure()
        sdb.execSQL("INSERT OR IGNORE INTO uuid_map_groups(group_uuid, local_id) VALUES (?,?)", arrayOf(groupUuid, localId))
    }
    suspend fun putDevice(deviceUuid: String, localId: Long) = withContext(Dispatchers.IO) {
        ensure()
        sdb.execSQL("INSERT OR IGNORE INTO uuid_map_devices(device_uuid, local_id) VALUES (?,?)", arrayOf(deviceUuid, localId))
    }
}