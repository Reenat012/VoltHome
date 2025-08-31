package ru.mugalimov.volthome.data.repository.impl

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.core.error.GroupNotFoundException
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.mapper.mapToDomainDevices
import ru.mugalimov.volthome.domain.mapper.mapToDomainGroups
import ru.mugalimov.volthome.domain.mapper.mapToDomainGroupsFromRelations
import ru.mugalimov.volthome.domain.mapper.mapToGroupEntities
import ru.mugalimov.volthome.domain.mapper.toDomainDevice
import ru.mugalimov.volthome.domain.mapper.toDomainGroup
import ru.mugalimov.volthome.domain.mapper.toDomainGroupFromRelation
import ru.mugalimov.volthome.domain.mapper.toEntityGroup
import ru.mugalimov.volthome.domain.mapper.toEntityJoin
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.phase_load.GroupWithDevices
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator
import javax.inject.Inject

class ExplicationRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val groupDeviceJoinDao: GroupDeviceJoinDao,
    private val deviceDao: DeviceDao,
    private val db: AppDatabase,
    //свойство dispatchers, которое хранит диспетчер для запуска корутин
    //в фоновых потоках, подходящих для IO-задач
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : ExplicationRepository {
    override fun observeAllGroup(): Flow<List<CircuitGroup>> {
        return groupDao.observeGroupsWithDevices()
            .map { groupsWithDevices ->
                groupsWithDevices.map { it.toDomainGroupFromRelation() }
            }
    }

    override suspend fun getGroupsWithDevices(): List<GroupWithDevices> {
        val groups = groupDao.getAllGroups()
        return groups.map { entity ->
            val devices = deviceDao.getDevicesForGroup(entity.groupId)
            GroupWithDevices(
                group = entity.toDomainGroup(devices.map { it.toDomainDevice() }),
                devices = devices.map { it.toDomainDevice() }
            )
        }
    }

    override fun observeGroupsWithDevices(): Flow<List<CircuitGroupWithDevices>> {
        val groupsFlow  = groupDao.observeAllGroups()         // Flow<List<CircuitGroupEntity>>
//        val devicesFlow = deviceDao.observeDevicesByIdRoom    // ← этого мало!
        // ↑ нужен поток ВСЕХ устройств, а не только по комнате. Добавим общий:
        // В DeviceDao:
        // @Query("SELECT * FROM devices")
        // fun observeAllDevices(): Flow<List<DeviceEntity>>

        val allDevicesFlow = deviceDao.observeDevices()
        val joinsFlow   = groupDeviceJoinDao.observeJoins()   // Flow<List<GroupDeviceJoin>>

        return combine(groupsFlow, allDevicesFlow, joinsFlow) { groups, devices, joins ->
            Log.d("LOADS_REPO", "groups=${groups.size} devices=${devices.size} joins=${joins.size}")
            // Готовим быстрый доступ
            val devicesById = devices.associateBy { it.deviceId }
            val deviceIdsByGroup: Map<Long, Set<Long>> =
                joins.groupBy({ it.groupId }, { it.deviceId })
                    .mapValues { (_, ids) -> ids.toHashSet() }

            groups.map { g ->
                val ids = deviceIdsByGroup[g.groupId].orEmpty()
                val devs = ids.mapNotNull { devicesById[it] }
                CircuitGroupWithDevices(group = g, devices = devs)
            }
        }
        // .distinctUntilChanged() // можно включить, если будут дубли
    }


    override suspend fun addGroup(circuitGroups: List<CircuitGroup>) {
        withContext(dispatchers) {
            // берём экземпляр БД (он у вас уже есть в проекте)
            val db = AppDatabase.getInstance(context)

            db.withTransaction {
                Log.d("Repo", "Транзакционно заменяем ${circuitGroups.size} групп")

                // 1) полная очистка таблицы групп (джоины удалятся каскадом)
                groupDao.deleteAllGroups()

                // 2) вставка групп и связей
                circuitGroups.forEach { group ->
                    // ВАЖНО: сбрасываем groupId в 0, чтобы Room сгенерировал новый PK
                    val entityToInsert = group.toEntityGroup().copy(groupId = 0)

                    // получаем фактически сгенерированный PK
                    val newGroupId = groupDao.addGroup(entityToInsert)

                    // 3) вставляем связи с новыми PK
                    group.devices.forEach { device ->
                        groupDeviceJoinDao.insertJoin(
                            GroupDeviceJoin(
                                groupId = newGroupId,
                                deviceId = device.id
                            )
                        )
                    }
                }
            }
        }
    }

//
//    override suspend fun addGroup(circuitGroups: List<CircuitGroup>) {
//        withContext(dispatchers) {
//            Log.d("Repo", "Сохраняем ${circuitGroups.size} групп")
//            try {
//                circuitGroups.forEach { group ->
//                    // Сохраняем группу
//                    val groupEntity = group.toEntityGroup()
//                    val groupId = groupDao.addGroup(groupEntity)
//                    Log.d(
//                        "Repo",
//                        "Inserted group with ID = $groupId, номер группы = ${group.groupNumber}"
//                    )
//
//                    // Сохраняем связи с устройствами
//                    group.devices.forEach { device ->
//                        groupDeviceJoinDao.insertJoin(
//                            GroupDeviceJoin(
//                                groupId = groupId,
//                                deviceId = device.id
//                            )
//                        )
//                    }
//                }
//            } catch (e: Exception) {
//                throw GroupNotFoundException("Ошибка при сохранении групп: ${e.message}")
//            }
//        }
//    }

    override suspend fun updateGroup(groupId: Long) {
        // Получаем группу с устройствами
        val groupWithDevices = groupDao.getGroupWithDevicesById(groupId)
            ?: throw GroupNotFoundException("Группа $groupId не найдена")

        // Рассчитываем новый ток
        val newCurrent = groupWithDevices.devices
            .mapToDomainDevices()
            .sumOf { d ->
                CurrentCalculator.calculateNominalCurrent(
                    power        = d.power.toDouble(),
                    voltage      = (d.voltage.value.takeIf { it > 0 } ?: 230).toDouble(),
                    powerFactor  = d.powerFactor,
                    demandRatio  = d.demandRatio,
                    voltageType  = d.voltage.type
                )
            }

        // Обновляем или удаляем группу
        if (newCurrent == 0.0) {
            groupDao.deleteGroupByGroupId(groupId)
        } else {
            groupDao.updateGroupCurrent(groupId, newCurrent)
        }
    }

    override suspend fun getAllGroups(): List<CircuitGroup> {
        return groupDao.getAllGroupsWithDevices().mapToDomainGroupsFromRelations()
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
        return groupDeviceJoinDao.getDevicesForGroup(groupId).mapToDomainDevices()
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
        return groupDao.getGroupWithDevicesById(groupId)?.toDomainGroupFromRelation()
    }

    override suspend fun getGroupByRoom(roomName: String): List<CircuitGroup> =
        withContext(dispatchers) {
            return@withContext try {
                groupDao.getGroupByRoom(roomName).mapToDomainGroups()
            } catch (e: Exception) {
                throw GroupNotFoundException()
            }
        }

    override suspend fun getGroupByType(groupType: DeviceType): List<CircuitGroup> =
        withContext(dispatchers) {
            return@withContext try {
                groupDao.getGroupByType(groupType).mapToDomainGroups()
            } catch (e: Exception) {
                throw GroupNotFoundException()
            }
        }

    override suspend fun replaceAllGroupsTransactional(groups: List<CircuitGroup>) =
        withContext(dispatchers) {
            db.withTransaction {
                // 0) (опционально) если нет CASCADE — чистим join-таблицу явно
                // groupDeviceJoinDao.deleteAll()

                // 1) чистим группы (если есть CASCADE — join-ы сотрутся автоматически)
                groupDao.deleteAllGroups()

                // 2) готовим сущности; важно: сбросить groupId = 0 для авто-генерации
                val groupEntities = groups.map { it.toEntityGroup().copy(groupId = 0) }

                // 3) вставка групп — ПОЛУЧАЕМ сгенерированные id
                val newIds: List<Long> = groupDao.insertGroups(groupEntities)

                // safety: размерности обязаны совпасть
                check(newIds.size == groups.size) {
                    "insertGroups returned ${newIds.size} ids for ${groups.size} groups"
                }
                // safety: все id > 0 (иначе Room что-то не сгенерировал)
                require(newIds.all { it > 0L }) { "insertGroups returned non-positive id(s): $newIds" }

                // 4) строим join-ы ПО ИНДЕКСАМ (Room возвращает id в порядке списка)
                val joins = buildList {
                    groups.forEachIndexed { idx, g ->
                        val newGroupId = newIds[idx]

                        // если у группы нет устройств — пропускаем
                        if (g.devices.isEmpty()) return@forEachIndexed

                        g.devices.forEach { d ->
                            // safety: deviceId должен быть валиден
                            require(d.id > 0L) { "Device id must be > 0 for join. Device=${d.name}" }

                            add(
                                GroupDeviceJoin(
                                    groupId = newGroupId,
                                    deviceId = d.id
                                )
                            )
                        }
                    }
                }

                // 5) вставляем join-ы
                if (joins.isNotEmpty()) {
                    groupDeviceJoinDao.insertAll(joins)
                }
            }
        }
}