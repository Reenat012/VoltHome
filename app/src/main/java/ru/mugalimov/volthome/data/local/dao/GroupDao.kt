package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceType

@Dao
interface GroupDao {
    @Transaction
    @Query("SELECT * FROM groups")
    fun observeGroupsWithDevices(): Flow<List<CircuitGroupWithDevices>>

    @Query("SELECT * FROM groups")
    fun observeAllGroups(): Flow<List<CircuitGroupEntity>>

    @Query("SELECT * FROM groups")
    suspend fun getAllGroups() : List<CircuitGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addGroup(group: CircuitGroupEntity): Long

    @Query("SELECT * FROM groups WHERE group_id = :id")
    suspend fun getGroupById(id: Long) : CircuitGroupEntity?

    @Query("SELECT * FROM groups WHERE room_name = :roomName")
    suspend fun getGroupByRoom(roomName: String) : List<CircuitGroupEntity>

    @Query("SELECT * FROM groups WHERE group_type = :groupType")
    suspend fun getGroupByType(groupType: DeviceType) : List<CircuitGroupEntity>

    // Удаляет все группы, связанные с комнатой по её ID
    @Query("DELETE FROM groups WHERE room_id = :roomId")
    suspend fun deleteGroupByRoomId(roomId: Long)

    @Query("DELETE FROM groups WHERE group_id = :groupId")
    suspend fun deleteGroupByGroupId(groupId: Long)

    @Query("DELETE FROM groups")
    suspend fun deleteAllGroups()

    @Query("UPDATE groups SET nominal_current = :current WHERE group_id = :groupId")
    suspend fun updateGroupCurrent(groupId: Long, current: Double)

    @Transaction
    @Query("SELECT * FROM groups")
    suspend fun getAllGroupsWithDevices(): List<CircuitGroupWithDevices>

    @Transaction
    @Query("SELECT * FROM groups WHERE group_id = :groupId")
    suspend fun getGroupWithDevicesById(groupId: Long): CircuitGroupWithDevices?


}