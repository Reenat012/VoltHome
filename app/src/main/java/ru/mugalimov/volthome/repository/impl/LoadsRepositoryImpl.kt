package ru.mugalimov.volthome.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.dao.LoadDao
import ru.mugalimov.volthome.entity.DeviceEntity
import ru.mugalimov.volthome.entity.LoadEntity
import ru.mugalimov.volthome.model.Device
import ru.mugalimov.volthome.model.Load
import ru.mugalimov.volthome.module.IoDispatcher
import ru.mugalimov.volthome.repository.LoadsRepository
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
                current = current,
                sumPower = sumPower,
                countDevices = countDevice,
                roomId = roomId,
                createdAt = Date()
            )
        )
    }
}

private fun List<LoadEntity>.toDomainModelListLoad(): List<Load> {
    return map { entity ->
        Load(
            id = entity.id,
            name = entity.name,
            current = entity.current,
            sumPower = entity.sumPower,
            countDevices = entity.countDevices,
            createdAt = entity.createdAt,
            roomId = entity.roomId
        )
    }
}


private fun LoadEntity.toDomainModelLoad() = Load(
    id = id,
    name = name,
    current = current,
    sumPower = sumPower,
    countDevices = countDevices,
    createdAt = createdAt,
    roomId = roomId
)