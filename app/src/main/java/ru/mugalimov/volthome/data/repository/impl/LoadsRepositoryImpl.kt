package ru.mugalimov.volthome.data.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.domain.model.Load
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.data.repository.LoadsRepository
import ru.mugalimov.volthome.domain.mapper.toDomainModelListLoad
import java.util.Date
import javax.inject.Inject

class LoadsRepositoryImpl @Inject constructor(
    private val loadDao: LoadDao,
    private val groupDao: GroupDao,
    private val deviceDao: DeviceDao,
    private val joinDao: GroupDeviceJoinDao,
    @IoDispatcher private val dispatchers: CoroutineDispatcher
) :
    LoadsRepository {
    override suspend fun observeLoads(): Flow<List<Load>> {
        return loadDao.observeLoads()
            .map { entities -> entities.toDomainModelListLoad() } //преобразуем из entityLoad в Load
            .flowOn(dispatchers) //корутина
    }

    override fun observeGroupsWithDevices(): Flow<List<CircuitGroupWithDevices>> {
        val groupsFlow  = groupDao.observeAllGroups()
        val devicesFlow = deviceDao.observeDevices()
        val joinsFlow   = joinDao.observeJoins()

        return combine(groupsFlow, devicesFlow, joinsFlow) { groups, devices, joins ->
            // строим devices для каждой группы через join-таблицу
            val devicesByGroup: Map<Long, List<DeviceEntity>> =
                joins.groupBy({ it.groupId }, { it.deviceId })
                    .mapValues { (_, deviceIds) ->
                        val idSet = deviceIds.toHashSet()
                        devices.filter { it.deviceId in idSet }
                    }

            groups.map { g ->
                CircuitGroupWithDevices(
                    group = g,
                    devices = devicesByGroup[g.groupId].orEmpty()
                )
            }
        }
    }

    override suspend fun addLoads(
        name: String,
        current: Double,
        sumPower: Int,
        countDevice: Int,
        roomId: Long
    ) {
        loadDao.addLoad(
            LoadEntity(
                name = name,
                currentRoom = current,
                powerRoom = sumPower,
                countDevices = countDevice,
                roomId = roomId,
                createdAt = Date()
            )
        )
    }

    override suspend fun getLoadForRoom(roomId: Long): LoadEntity? = withContext(dispatchers) {
        // Для совместимости с Room возвращаем первый элемент, если есть
        loadDao.getLoadForRoom(roomId).firstOrNull()
    }
    override suspend fun upsertAllLoads(loads: List<LoadEntity>) = withContext(dispatchers) {
        val validLoads = loads.filterNotNull()
        if (validLoads.isNotEmpty()) {
            loadDao.upsertAllLoads(validLoads)
        }

    }

    override suspend fun getAllLoads(): List<LoadEntity> = withContext(dispatchers) {
        loadDao.observeLoads().first()
    }
}