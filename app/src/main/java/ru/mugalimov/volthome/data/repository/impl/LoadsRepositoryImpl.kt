package ru.mugalimov.volthome.data.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.dao.LoadDao
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.domain.model.Load
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.data.repository.LoadsRepository
import java.util.Date
import javax.inject.Inject

class LoadsRepositoryImpl @Inject constructor(
    private val loadDao: LoadDao,
    @IoDispatcher private val dispatchers: CoroutineDispatcher
) :
    LoadsRepository {
    override suspend fun observeLoads(): Flow<List<Load>> {
        return loadDao.observeLoads()
            .map { entities -> entities.toDomainModelListLoad() } //преобразуем из entityLoad в Load
            .flowOn(dispatchers) //корутина
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

private fun List<LoadEntity>.toDomainModelListLoad(): List<Load> {
    return map { entity ->
        Load(
            id = entity.id,
            name = entity.name,
            current = entity.currentRoom,
            sumPower = entity.powerRoom,
            countDevices = entity.countDevices,
            createdAt = entity.createdAt,
            roomId = entity.roomId
        )
    }
}


private fun LoadEntity.toDomainModelLoad() = Load(
    id = id,
    name = name,
    current = currentRoom,
    sumPower = powerRoom,
    countDevices = countDevices,
    createdAt = createdAt,
    roomId = roomId
)