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
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
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
            .map { groupsWithDevices ->
                groupsWithDevices.map { it.toDomainModel() }
            }
    }

    override suspend fun addGroup(circuitGroups: List<CircuitGroup>) {
        withContext(dispatchers) {
            try {
                circuitGroups.forEach { group ->
                    // Сохраняем группу
                    val groupEntity = group.toEntity()
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
                throw GroupNotFoundException("Ошибка при сохранении групп: ${e.message}")
            }
        }
    }

    override suspend fun updateGroup(groupId: Long) {
        // Получаем группу с устройствами
        val groupWithDevices = groupDao.getGroupWithDevicesById(groupId)
            ?: throw GroupNotFoundException("Группа $groupId не найдена")

        // Рассчитываем новый ток
        val newCurrent = groupWithDevices.devices
            .toDomainModelListDevice()
            .sumOf {
                (it.power * it.demandRatio) / (it.voltage.value * it.powerFactor)
            }

        // Обновляем или удаляем группу
        if (newCurrent == 0.0) {
            groupDao.deleteGroupByGroupId(groupId)
        } else {
            groupDao.updateGroupCurrent(groupId, newCurrent)
        }
    }

    override suspend fun getAllGroups(): List<CircuitGroup> {
        return groupDao.getAllGroupsWithDevices().toDomainModelList()
    }

    override suspend fun deleteAllGroups() {
        withContext(dispatchers) {
            groupDao.deleteAllGroups()
        }
    }

    override suspend fun addDeviceToGroup(deviceId: Long, groupId: Long) {
        groupDeviceJoinDao.insertJoin(GroupDeviceJoin(groupId, deviceId))
    }

    override suspend fun getDevicesForGroup(groupId: Long): List<Device> {
        return groupDeviceJoinDao.getDevicesForGroup(groupId).toDomainModelListDevice()
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

    override suspend fun getGroupById(groupId: Long): CircuitGroup? {
        return groupDao.getGroupWithDevicesById(groupId)?.toDomainModel()
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

private fun CircuitGroupWithDevices.toDomainModel(): CircuitGroup {
    return CircuitGroup(
        groupId = group.groupId,
        groupNumber = group.groupNumber,
        roomName = group.roomName,
        roomId = group.roomId,
        groupType = DeviceType.valueOf(group.groupType),
        devices = devices.toDomainModelListDevice(),
        nominalCurrent = group.nominalCurrent,
        circuitBreaker = group.circuitBreaker,
        cableSection = group.cableSection,
        breakerType = group.breakerType,
        rcdRequired = group.rcdRequired,
        rcdCurrent = group.rcdCurrent
    )
}

private fun List<CircuitGroupWithDevices>.toDomainModelList(): List<CircuitGroup> {
    return map { it.toDomainModel() }
}

private fun CircuitGroup.toEntity(): CircuitGroupEntity {
    return CircuitGroupEntity(
        groupId = groupId,
        groupNumber = groupNumber,
        roomId = roomId,
        roomName = roomName,
        groupType = groupType.name,
        nominalCurrent = nominalCurrent,
        circuitBreaker = circuitBreaker,
        cableSection = cableSection,
        breakerType = breakerType,
        rcdRequired = rcdRequired,
        rcdCurrent = rcdCurrent
    )
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

private fun List<CircuitGroupEntity>.toDomainModelListGroup(): List<CircuitGroup> {
    return map { entity ->
        CircuitGroup(
            groupId = entity.groupId,
            groupNumber = entity.groupNumber,
            roomName = entity.roomName,
            roomId = entity.roomId,
            groupType = DeviceType.valueOf(entity.groupType),
            nominalCurrent = entity.nominalCurrent,
            circuitBreaker = entity.circuitBreaker,
            cableSection = entity.cableSection,
            breakerType = entity.breakerType,
            rcdRequired = entity.rcdRequired,
            rcdCurrent = entity.rcdCurrent,
            devices = emptyList() // Заполняется отдельно
        )
    }
}