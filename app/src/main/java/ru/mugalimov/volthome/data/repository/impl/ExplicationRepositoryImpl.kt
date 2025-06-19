package ru.mugalimov.volthome.data.repository.impl

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.core.error.GroupNotFoundException
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import javax.inject.Inject

class ExplicationRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val groupDeviceJoinDao: GroupDeviceJoinDao,
    private val deviceDao: DeviceDao,
    //свойство dispatchers, которое хранит диспетчер для запуска корутин
    //в фоновых потоках, подходящих для IO-задач
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : ExplicationRepository {
    override suspend fun observeAllGroup(): Flow<List<CircuitGroup>> {
        return groupDao.observeGroupsWithDevices()
            .map { list ->
                list.map { entity ->
                    CircuitGroup(
                        groupId = entity.group.groupId,
                        roomId = entity.group.roomId,
                        roomName = entity.group.roomName,
                        groupType = entity.group.groupType,
                        nominalCurrent = entity.group.nominalCurrent,
                        circuitBreaker = entity.group.circuitBreaker,
                        cableSection = entity.group.cableSection,
                        groupNumber = entity.group.groupNumber,
                        devices = entity.devices.toDomainModelListDevice()
                    )
                }
            }
    }

    override suspend fun addGroup(circuitGroups: List<CircuitGroup>) {
        withContext(dispatchers) {
            Log.d(TAG, "Группы из vm $circuitGroups")

//            groupDao.addGroup(circuitGroups.toDomainModelListGroupEntity())

            try {
                circuitGroups.forEach { group ->
                    // Сохраняем группу
                    val groupEntity = group.toDomainModelGroupEntity()

                    Log.d(TAG, "Группы в репозитории add group $group")
                    val groupId = groupDao.addGroup(groupEntity)

                    // Сохраняем связи с устройствами
                    group.devices.forEach { device ->
                        groupDeviceJoinDao.insertJoin(
                            GroupDeviceJoin(
                                groupId = groupId,
                                deviceId = device.id
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                throw GroupNotFoundException()
            }

        }
    }


    override suspend fun updateGroup(groupId: Long) {
        // Получаем текущие устройства группы
        val devicesEntity = deviceDao.getDevicesForGroup(groupId)

        // Преобразовываем из entity в domain
        val devicesDomain = devicesEntity.toDomainModelListDevice()

        // Рассчитываем новый ток
        val newCurrent = devicesDomain.sumOf { it.current }

        // Обновить или удалить группу
        if (newCurrent == 0.0) {
            groupDao.deleteGroupByGroupId(groupId)
        } else {
            groupDao.updateGroupCurrent(groupId, newCurrent)
        }
    }

    override suspend fun getAllGroups(): List<CircuitGroup> =
        withContext(dispatchers) {
            return@withContext try {
                val groups = groupDao.getAllGroups().toDomainModelListGroup()

                Log.d(TAG, "Группы в репозитрии getAllGroups(): $groups")

                groups

            } catch (e: Exception) {
                throw Exception("message: $e")
            }
        }

    override suspend fun deleteAllGroups() {
        withContext(dispatchers) {
            groupDao.deleteAllGroups()
        }
    }

    // Обработка удаления комнаты
    override suspend fun handleRoomDeletion(roomId: Long) {
        // Очистка связей
        groupDeviceJoinDao.deleteJoinsForRoom(roomId)
        // Группы удалятся автоматически через CASCADE
        groupDao.deleteGroupByRoomId(roomId)
    }

    // Обработка удаления устройства
    override suspend fun handleDeviceDeletion(deviceId: Long) {
        // Находим связанные группы
        val groupIds = groupDeviceJoinDao.getGroupIdsForDevice(deviceId)

        //Удаляем связи
        groupDeviceJoinDao.deleteJoinsForDevice(deviceId)

        //Обновляем группы
        groupIds.forEach { groupId ->
            groupDao.getGroupById(groupId)?.let { group ->
                updateGroup(group.groupId)
            }
        }
    }

    override suspend fun getGroupById(groupId: Long): CircuitGroup? =
        withContext(dispatchers) {
            return@withContext try {
                groupDao.getGroupById(groupId)?.toDomainModelGroup()
            } catch (e: Exception) {
                throw GroupNotFoundException()
            }
        }

    override suspend fun getGroupByRoom(roomName: String): List<CircuitGroup> =
        withContext(dispatchers) {
            return@withContext try {
                groupDao.getGroupByRoom(roomName).toDomainModelListGroup()
            } catch (e: Exception) {
                throw GroupNotFoundException()
            }
        }

    override suspend fun getGroupByType(groupType: DeviceType): List<CircuitGroup> =
        withContext(dispatchers) {
            return@withContext try {
                groupDao.getGroupByType(groupType).toDomainModelListGroup()
            } catch (e: Exception) {
                throw GroupNotFoundException()
            }
        }


}

private fun CircuitGroupEntity.toDomainModelGroup() = CircuitGroup(
    roomName = roomName,
    groupType = groupType,
    devices = devices,
    nominalCurrent = nominalCurrent,
    circuitBreaker = circuitBreaker,
    cableSection = cableSection,
    groupNumber = groupNumber,
    groupId = groupId,
    roomId = roomId
)

private fun CircuitGroup.toDomainModelGroupEntity() = CircuitGroupEntity(
    roomName = roomName,
    groupType = groupType,
    devices = devices,
    nominalCurrent = nominalCurrent,
    circuitBreaker = circuitBreaker,
    cableSection = cableSection,
    groupNumber = groupNumber,
    groupId = groupId,
    roomId = roomId
)

private fun List<CircuitGroupEntity>.toDomainModelListGroup(): List<CircuitGroup> {
    return map { entity ->
        CircuitGroup(
            roomName = entity.roomName,
            groupType = entity.groupType,
            devices = entity.devices,
            nominalCurrent = entity.nominalCurrent,
            circuitBreaker = entity.circuitBreaker,
            cableSection = entity.cableSection,
            groupNumber = entity.groupNumber,
            groupId = entity.groupId,
            roomId = entity.roomId
        )
    }
}

private fun List<CircuitGroup>.toDomainModelListGroupEntity(): List<CircuitGroupEntity> {
    return map { entity ->
        CircuitGroupEntity(
            roomName = entity.roomName,
            groupType = entity.groupType,
            devices = entity.devices,
            nominalCurrent = entity.nominalCurrent,
            circuitBreaker = entity.circuitBreaker,
            cableSection = entity.cableSection,
            groupNumber = entity.groupNumber,
            groupId = entity.groupId,
            roomId = entity.roomId
        )
    }
}

private fun List<DeviceEntity>.toDomainModelListDevice(): List<Device> {
    return map { entity ->
        Device(
            id = entity.deviceId,
            name = entity.name,
            power = entity.power,
            voltage = entity.voltage,
            demandRatio = entity.demandRatio,
            roomId = entity.roomId,
            createdAt = entity.createdAt,
            deviceType = entity.deviceType,
            powerFactor = entity.powerFactor
        )
    }
}