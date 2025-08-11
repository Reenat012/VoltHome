package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.phase_load.GroupWithDevices

interface ExplicationRepository {
    fun observeAllGroup(): Flow<List<CircuitGroup>>

    suspend fun addGroup(circuitGroups: List<CircuitGroup>)

    suspend fun handleRoomDeletion(roomId: Long)

    suspend fun handleDeviceDeletion(deviceId: Long)

    suspend fun getGroupById(groupId: Long) : CircuitGroup?

    suspend fun getGroupByRoom(roomName: String) : List<CircuitGroup>

    suspend fun getGroupByType(groupType: DeviceType) : List<CircuitGroup>

    suspend fun updateGroup(groupId: Long)

    suspend fun getAllGroups() : List<CircuitGroup>

    suspend fun deleteAllGroups()

    suspend fun addDeviceToGroup(deviceId: Long, groupId: Long)
    suspend fun getDevicesForGroup(groupId: Long): List<Device>

    suspend fun getGroupsWithDevices(): List<GroupWithDevices>

    fun observeGroupsWithDevices(): Flow<List<CircuitGroupWithDevices>>

}