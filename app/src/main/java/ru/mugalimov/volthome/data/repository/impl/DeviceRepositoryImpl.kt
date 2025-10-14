package ru.mugalimov.volthome.data.repository.impl

import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.core.error.DeviceNotFoundException
import ru.mugalimov.volthome.core.error.RoomNotFoundException
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.mapper.mapToDomainDevices
import ru.mugalimov.volthome.domain.mapper.toDomainDevice
import ru.mugalimov.volthome.domain.mapper.toEntityDevice
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.ui.components.JsonParser
import java.util.Date
import javax.inject.Inject
import android.content.Context

// 🔹 outbox / tombstones
import ru.mugalimov.volthome.data.local.dao.OutboxDao
import ru.mugalimov.volthome.data.local.dao.TombstoneDao
import ru.mugalimov.volthome.data.local.entity.OutboxEntity
import ru.mugalimov.volthome.data.local.entity.OutboxOpType
import ru.mugalimov.volthome.data.local.entity.TombstoneEntity
import ru.mugalimov.volthome.data.local.entity.TombstoneEntityType
import ru.mugalimov.volthome.data.sync.outbox.DeviceCreatePayload
import ru.mugalimov.volthome.data.sync.outbox.DeviceUpdatePayload
import ru.mugalimov.volthome.data.sync.outbox.DeviceDeletePayload
import ru.mugalimov.volthome.data.sync.outbox.OutboxPushWorker
import ru.mugalimov.volthome.data.sync.outbox.toJson

class DeviceRepositoryImpl @Inject constructor(
    private val deviceDao: DeviceDao,
    private val roomDao: RoomDao,
    private val explicationRepository: ExplicationRepository,
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
    private val appDb: AppDatabase,
    // 🔹 новое
    private val outboxDao: OutboxDao,
    private val tombstoneDao: TombstoneDao
) : DeviceRepository {

    private val uuidDao get() = appDb.uuidMapDao()

    // --------------------------- Observe ---------------------------

    override suspend fun observeDevicesByIdRoom(roomId: Long): Flow<List<Device>> {
        return deviceDao.observeDevicesByIdRoom(roomId)
            .map { entities -> entities.mapToDomainDevices() }
            .flowOn(dispatchers)
    }

    // --------------------------- Create ----------------------------

    override suspend fun addDevice(device: Device) {
        withContext(dispatchers) {
            try {
                // 1) Валидация/получение projectId
                val room = device.roomId?.let { roomDao.getRoomById(it) }
                    ?: throw IllegalArgumentException("roomId=null или комната не найдена")
                val projectId = room.projectId
                    ?: throw IllegalArgumentException("У комнаты нет projectId")

                // 2) Локальная вставка
                val createdAt = Date()
                val localId = deviceDao.addDevice(
                    DeviceEntity(
                        name = device.name,
                        power = device.power,
                        voltage = device.voltage,
                        demandRatio = device.demandRatio,
                        roomId = device.roomId,
                        createdAt = createdAt,
                        deviceType = device.deviceType,
                        powerFactor = device.powerFactor,
                        hasMotor = device.hasMotor,
                        requiresDedicatedCircuit = device.requiresDedicatedCircuit,
                        requiresSocketConnection = device.requiresSocketConnection,
                        projectId = projectId
                    )
                )

                // 3) Outbox → DEVICE_CREATE
                outboxDao.insert(
                    OutboxEntity(
                        project_id = projectId,
                        op_type = OutboxOpType.DEVICE_CREATE,
                        payload_json = DeviceCreatePayload(
                            projectId = projectId,
                            localId = localId,
                            roomLocalId = device.roomId!!,
                            name = device.name,
                            power = device.power,
                            voltage = device.voltage,
                            demandRatio = device.demandRatio,
                            createdAt = createdAt.time,
                            deviceType = device.deviceType,
                            powerFactor = device.powerFactor,
                            hasMotor = device.hasMotor,
                            requiresDedicatedCircuit = device.requiresDedicatedCircuit,
                            requiresSocketConnection = device.requiresSocketConnection
                        ).toJson(),
                        group_key = "device:create:$projectId:$localId"
                    )
                )

                // 4) Запускаем пушер (если сеть есть — уйдёт сразу; если нет — дождётся)
                OutboxPushWorker.enqueueProject(context, projectId)
            } catch (e: Exception) {
                Log.e("DeviceRepo", "Ошибка при добавлении устройства: ${e.message}", e)
                throw e
            }
        }
    }

    // --------------------------- Update ----------------------------

    override suspend fun updateDevice(device: Device) =
        withContext(dispatchers) {
            val current = deviceDao.getDeviceById(device.id.toInt())
                ?: throw DeviceNotFoundException("Устройство ${device.id} не найдено")

            // 1) локально
            deviceDao.update(
                device.toEntityDevice().copy(projectId = current.projectId)
            )

            // 2) outbox
            val pid = current.projectId
            if (!pid.isNullOrBlank()) {
                outboxDao.insert(
                    OutboxEntity(
                        project_id = pid,
                        op_type = OutboxOpType.DEVICE_UPDATE,
                        payload_json = DeviceUpdatePayload(
                            projectId = pid,
                            localId = device.id,
                            roomLocalId = device.roomId,   // может быть null — значит не меняли
                            name = device.name,
                            power = device.power,
                            voltage = device.voltage,
                            demandRatio = device.demandRatio,
                            deviceType = device.deviceType,
                            powerFactor = device.powerFactor,
                            hasMotor = device.hasMotor,
                            requiresDedicatedCircuit = device.requiresDedicatedCircuit,
                            requiresSocketConnection = device.requiresSocketConnection
                        ).toJson(),
                        group_key = "device:update:$pid:${device.id}"
                    )
                )
                OutboxPushWorker.enqueueProject(context, pid)
            }
        }

    // --------------------------- Delete (single) -------------------

    override suspend fun deleteDevice(deviceId: Long) {
        withContext(dispatchers) {
            val existing = deviceDao.getDeviceById(deviceId.toInt())
                ?: throw DeviceNotFoundException("Устройство с ID $deviceId не найдено")

            val projectId = existing.projectId
            if (projectId.isNullOrBlank()) {
                // Без projectId удаляем просто локально
                val rows = deviceDao.deleteDeviceById(deviceId)
                if (rows == 0) throw DeviceNotFoundException("Устройство с ID $deviceId не найдено")
                explicationRepository.handleDeviceDeletion(deviceId)
                return@withContext
            }

            // 1) tombstone — чтобы UI/квери исключали
            tombstoneDao.insert(
                TombstoneEntity(
                    project_id = projectId,
                    entity_type = TombstoneEntityType.DEVICE,
                    local_id = deviceId,
                    server_uuid = uuidDao.getDeviceUuidByLocal(deviceId)
                )
            )

            // 2) outbox DEVICE_DELETE
            outboxDao.insert(
                OutboxEntity(
                    project_id = projectId,
                    op_type = OutboxOpType.DEVICE_DELETE,
                    payload_json = DeviceDeletePayload(
                        projectId = projectId,
                        localId = deviceId,
                        serverUuid = uuidDao.getDeviceUuidByLocal(deviceId)
                    ).toJson(),
                    group_key = "device:delete:$projectId:$deviceId"
                )
            )

            // 3) локально удаляем и обслуживаем каскад
            val rowsDeleted = deviceDao.deleteDeviceById(deviceId)
            if (rowsDeleted == 0) throw DeviceNotFoundException("Устройство с ID $deviceId не найдено")
            explicationRepository.handleDeviceDeletion(deviceId)

            // 4) пушер
            OutboxPushWorker.enqueueProject(context, projectId)
        }
    }

    // --------------------------- Read ------------------------------

    override suspend fun getDeviceById(deviceId: Int): Device? =
        withContext(dispatchers) {
            try {
                val entity = deviceDao.getDeviceById(deviceId)
                entity?.toDomainDevice()
            } catch (_: Exception) {
                throw DeviceNotFoundException()
            }
        }

    override suspend fun getAllDevicesByRoomId(roomId: Long): List<Device> =
        withContext(dispatchers) {
            try {
                deviceDao.getAllDevicesByRoomId(roomId).mapToDomainDevices()
            } catch (_: Exception) {
                throw RoomNotFoundException()
            }
        }

    override fun getDefaultDevices(): Flow<List<DefaultDevice>> {
        return try {
            JsonParser.parseDevices(context)
        } catch (_: Exception) {
            emptyFlow()
        }
    }

    override suspend fun getAllDevices(): List<Device> =
        withContext(dispatchers) {
            try {
                deviceDao.getAllDevices().mapToDomainDevices()
            } catch (_: Exception) {
                throw DeviceNotFoundException()
            }
        }
}