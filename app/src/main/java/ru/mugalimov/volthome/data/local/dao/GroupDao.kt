package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
import ru.mugalimov.volthome.domain.model.DeviceType

@Dao
interface GroupDao {

    @Transaction
    @Query("SELECT * FROM `groups` WHERE project_id = :projectId")
    fun observeGroupsWithDevicesByProject(projectId: String): Flow<List<CircuitGroupWithDevices>>

    @Query("SELECT * FROM `groups` WHERE project_id = :projectId")
    fun observeAllGroupsByProject(projectId: String): Flow<List<CircuitGroupEntity>>

    @Query("SELECT * FROM `groups` WHERE project_id = :projectId")
    suspend fun getAllGroupsByProject(projectId: String): List<CircuitGroupEntity>

    @Transaction
    @Query("SELECT * FROM `groups`")
    fun observeGroupsWithDevices(): Flow<List<CircuitGroupWithDevices>>

    @Query("SELECT * FROM `groups`")
    fun observeAllGroups(): Flow<List<CircuitGroupEntity>>

    @Query("SELECT * FROM `groups`")
    suspend fun getAllGroups(): List<CircuitGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addGroup(group: CircuitGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<CircuitGroupEntity>): List<Long>

    @Query("SELECT * FROM `groups` WHERE group_id = :id")
    suspend fun getGroupById(id: Long): CircuitGroupEntity?

    @Query("SELECT * FROM `groups` WHERE room_name = :roomName")
    suspend fun getGroupByRoom(roomName: String): List<CircuitGroupEntity>

    /**
     * Важно: в БД group_type — String.
     * Поэтому сюда должен приходить String (например DeviceType.SOCKET.name).
     */
    @Query("SELECT * FROM `groups` WHERE group_type = :groupType")
    suspend fun getGroupByType(groupType: String): List<CircuitGroupEntity>

    @Query("DELETE FROM `groups` WHERE room_id = :roomId")
    suspend fun deleteGroupByRoomId(roomId: Long)

    @Query("DELETE FROM `groups` WHERE group_id = :groupId")
    suspend fun deleteGroupByGroupId(groupId: Long)

    /**
     * ❌ ОПАСНО: удаляет группы всех проектов.
     * Использовать только для dev/тестов/сброса БД в отладочных сценариях.
     * В прод-коде запрещено — используйте deleteAllGroupsByProject(projectId).
     */
    @Deprecated(
        message = "ОПАСНО: удаляет группы всех проектов. Используйте deleteAllGroupsByProject(projectId).",
        replaceWith = ReplaceWith("deleteAllGroupsByProject(projectId)"),
        level = DeprecationLevel.ERROR
    )
    @Query("DELETE FROM `groups`")
    suspend fun deleteAllGroups()

    @Query("UPDATE `groups` SET nominal_current = :current WHERE group_id = :groupId")
    suspend fun updateGroupCurrent(groupId: Long, current: Double)

    @Transaction
    @Query("SELECT * FROM `groups`")
    suspend fun getAllGroupsWithDevices(): List<CircuitGroupWithDevices>

    @Transaction
    @Query("SELECT * FROM `groups` WHERE group_id = :groupId")
    suspend fun getGroupWithDevicesById(groupId: Long): CircuitGroupWithDevices?

    // ------- для Sync/проектных операций -------

    @Query("SELECT COUNT(*) FROM `groups` WHERE project_id = :projectId")
    suspend fun countByProjectId(projectId: String): Int

    @Query("UPDATE `groups` SET project_id = :newId WHERE project_id = :oldId")
    suspend fun rebindProjectGroups(oldId: String, newId: String): Int

    @Query("DELETE FROM `groups` WHERE project_id = :projectId")
    suspend fun deleteGroupsByProject(projectId: String): Int

    /**
     * Возвращает id всех групп конкретного проекта.
     * Важно: используем РЕАЛЬНЫЕ имена таблицы/колонок (`groups`, `group_id`, `project_id`)
     */
    @Query("SELECT group_id FROM `groups` WHERE project_id = :projectId")
    suspend fun getGroupIdsByProject(projectId: String): List<Long>

    /**
     * Удаляет все группы только в рамках конкретного проекта.
     * Важно: удаление по `project_id`, а не “всех вообще”.
     */
    @Query("DELETE FROM `groups` WHERE project_id = :projectId")
    suspend fun deleteAllGroupsByProject(projectId: String)
}