package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin

@Dao
interface GroupDeviceJoinDao {

    /**
     * Таблица `group_device_join` — **единственный источник истины** для membership (устройство ↔ группа).
     *
     * Важно:
     * - В таблице **нет `projectId`**.
     * - Поэтому любые массовые операции должны быть ограничены списком `group_id`,
     *   полученным из `groups` конкретного `projectId`.
     */
    // -------------------- OBSERVE --------------------

    @Query("SELECT * FROM group_device_join")
    fun observeJoins(): Flow<List<GroupDeviceJoin>>

    // -------------------- READ (dbState для diff-commit) --------------------

    /**
     * Снимок join'ов по списку групп проекта (это наш projectId boundary).
     * Используется для вычисления joinsToInsert/joinsToDelete.
     */
    @Query("SELECT * FROM group_device_join WHERE group_id IN (:groupIds)")
    suspend fun getJoinsForGroupIds(groupIds: List<Long>): List<GroupDeviceJoin>

    // -------------------- INSERT --------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJoin(join: GroupDeviceJoin)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(joins: List<GroupDeviceJoin>)

    // -------------------- READ helpers --------------------

    @Query("SELECT device_id FROM group_device_join WHERE group_id = :groupId")
    suspend fun getDeviceIdsForGroup(groupId: Long): List<Long>

    @Query("SELECT group_id FROM group_device_join WHERE device_id = :deviceId")
    suspend fun getGroupIdsForDevice(deviceId: Long): List<Long>

    @Query(
        """
        SELECT d.* FROM devices d
        INNER JOIN group_device_join j ON j.device_id = d.device_id
        WHERE j.group_id = :groupId
        """
    )
    suspend fun getDevicesForGroup(groupId: Long): List<DeviceEntity>

    // -------------------- DELETE helpers --------------------

    @Query("DELETE FROM group_device_join")
    suspend fun deleteAll()

    @Query("DELETE FROM group_device_join WHERE device_id = :deviceId")
    suspend fun deleteJoinsForDevice(deviceId: Long)

    /**
     * Батч-удаление join'ов по deviceIds.
     * Удобно для diff: перед вставкой desired join'ов по затронутым устройствам.
     *
     * Важно: device_id — глобальный PK devices, поэтому удаление по deviceIds не затрагивает “другие проекты”
     * (устройства физически разные).
     */
    @Query("DELETE FROM group_device_join WHERE device_id IN (:deviceIds)")
    suspend fun deleteJoinsForDeviceIds(deviceIds: List<Long>): Int

    /**
     * Удаляет все join'ы конкретной группы.
     * Используется при groupsToDelete (сначала joins → потом groups).
     */
    @Query("DELETE FROM group_device_join WHERE group_id = :groupId")
    suspend fun deleteJoinsForGroupId(groupId: Long): Int

    /**
     * Удаление всех связей для групп, принадлежащих комнате.
     */
    @Query(
        """
        DELETE FROM group_device_join
        WHERE group_id IN (SELECT group_id FROM `groups` WHERE room_id = :roomId)
        """
    )
    suspend fun deleteJoinsForRoom(roomId: Long)

    /**
     * Удаляет связи "группа-устройство" по списку group_id.
     */
    @Query("DELETE FROM group_device_join WHERE group_id IN (:groupIds)")
    suspend fun deleteJoinsForGroupIds(groupIds: List<Long>)
}