package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.domain.model.DeviceType

@Dao
interface GroupDao {

    // --- НОВОЕ: проектные выборки
    @Transaction
    @Query("SELECT * FROM `groups` WHERE project_id = :projectId")
    fun observeGroupsWithDevicesByProject(projectId: String): Flow<List<CircuitGroupWithDevices>>

    @Query("SELECT * FROM `groups` WHERE project_id = :projectId")
    fun observeAllGroupsByProject(projectId: String): Flow<List<CircuitGroupEntity>>

    @Query("SELECT * FROM `groups` WHERE project_id = :projectId")
    suspend fun getAllGroupsByProject(projectId: String): List<CircuitGroupEntity>

    // --- СТАРОЕ (до полной интеграции):
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

    @Query("SELECT * FROM `groups` WHERE group_type = :groupType")
    suspend fun getGroupByType(groupType: DeviceType): List<CircuitGroupEntity>

    @Query("DELETE FROM `groups` WHERE room_id = :roomId")
    suspend fun deleteGroupByRoomId(roomId: Long)

    @Query("DELETE FROM `groups` WHERE group_id = :groupId")
    suspend fun deleteGroupByGroupId(groupId: Long)

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
}