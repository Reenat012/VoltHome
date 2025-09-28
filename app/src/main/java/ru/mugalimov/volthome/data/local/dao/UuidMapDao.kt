package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.mugalimov.volthome.data.local.entity.*

@Dao
interface UuidMapDao {

    // -------- forward (uuid -> local) ----------
    @Query("SELECT local_id FROM uuid_map_rooms WHERE room_uuid = :uuid LIMIT 1")
    suspend fun getRoomLocal(uuid: String): Long?

    @Query("SELECT local_id FROM uuid_map_groups WHERE group_uuid = :uuid LIMIT 1")
    suspend fun getGroupLocal(uuid: String): Long?

    @Query("SELECT local_id FROM uuid_map_devices WHERE device_uuid = :uuid LIMIT 1")
    suspend fun getDeviceLocal(uuid: String): Long?

    // -------- reverse (local -> uuid) ----------
    @Query("SELECT room_uuid FROM uuid_map_rooms WHERE local_id = :localId LIMIT 1")
    suspend fun getRoomUuidByLocal(localId: Long): String?

    @Query("SELECT group_uuid FROM uuid_map_groups WHERE local_id = :localId LIMIT 1")
    suspend fun getGroupUuidByLocal(localId: Long): String?

    @Query("SELECT device_uuid FROM uuid_map_devices WHERE local_id = :localId LIMIT 1")
    suspend fun getDeviceUuidByLocal(localId: Long): String?

    // -------- upserts (IGNORE: не перезаписываем существующее) ----------
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putRooms(items: List<UuidMapRoom>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putGroups(items: List<UuidMapGroup>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putDevices(items: List<UuidMapDevice>)
}