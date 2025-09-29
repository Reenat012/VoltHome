package ru.mugalimov.volthome.data.repository.impl

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.core.error.DeviceNotFoundException
import ru.mugalimov.volthome.core.error.RoomNotFoundException
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.data.remote.dto.OpBucket
import ru.mugalimov.volthome.data.remote.dto.Ops
import ru.mugalimov.volthome.data.remote.dto.ProjectBatchRequest
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

class DeviceRepositoryImpl @Inject constructor(
    private val deviceDao: DeviceDao,
    private val roomDao: RoomDao,
    private val explicationRepository: ExplicationRepository,
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
    // ↓↓↓ добавлено
    private val projectsApi: ProjectsApi,
    private val appDb: AppDatabase
) : DeviceRepository {

    private val uuidDao get() = appDb.uuidMapDao()

    private val defaultDevicesCache by lazy(LazyThreadSafetyMode.NONE) {
        JsonParser.parseDevices(context)
    }

    override suspend fun observeDevicesByIdRoom(roomId: Long): Flow<List<Device>> {
        return deviceDao.observeDevicesByIdRoom(roomId)
            .map { entities -> entities.mapToDomainDevices() }
            .flowOn(dispatchers)
    }

    override suspend fun addDevice(device: Device) {
        try {
            withContext(dispatchers) {
                val room = device.roomId?.let { roomDao.getRoomById(it) }
                    ?: throw IllegalArgumentException("У устройства нет привязки к комнате (roomId=null) или комната не найдена")

                deviceDao.addDevice(
                    DeviceEntity(
                        name = device.name,
                        power = device.power,
                        voltage = device.voltage,
                        demandRatio = device.demandRatio,
                        roomId = device.roomId,
                        createdAt = Date(),
                        deviceType = device.deviceType,
                        powerFactor = device.powerFactor,
                        hasMotor = device.hasMotor,
                        requiresDedicatedCircuit = device.requiresDedicatedCircuit,
                        requiresSocketConnection = device.requiresSocketConnection,
                        projectId = room.projectId
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при добавлении устройства: ${e.message}", e)
            throw e
        }
    }

    override suspend fun deleteDevice(deviceId: Long) {
        withContext(dispatchers) {
            val existing = deviceDao.getDeviceById(deviceId.toInt()) ?: throw DeviceNotFoundException("Устройство с ID $deviceId не найдено")

            // 1) Пытаемся удалить на сервере
            val projectId = existing.projectId
            if (!projectId.isNullOrBlank() && !projectId.startsWith("draft-")) {
                val deviceUuid = uuidDao.getDeviceUuidByLocal(deviceId)
                if (deviceUuid != null) {
                    runCatching {
                        val ops = Ops(
                            rooms = null,
                            groups = null,
                            devices = OpBucket(upsert = emptyList(), delete = listOf(deviceUuid))
                        )
                        projectsApi.applyBatch(projectId, ProjectBatchRequest(baseVersion = null, ops = ops))
                        Log.i("DeviceDelete", "Server delete ok: deviceUuid=$deviceUuid project=$projectId")
                    }.onFailure { t ->
                        Log.w("DeviceDelete", "Server delete failed: ${t.message}", t)
                    }
                }
            }

            // 2) Локально удаляем
            val rowsDeleted = deviceDao.deleteDeviceById(deviceId)
            if (rowsDeleted == 0) throw DeviceNotFoundException("Устройство с ID $deviceId не найдено")
            explicationRepository.handleDeviceDeletion(deviceId)
        }
    }

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

    override suspend fun updateDevice(device: Device) =
        withContext(dispatchers) {
            val current = deviceDao.getDeviceById(device.id.toInt())
            deviceDao.update(
                device.toEntityDevice().copy(projectId = current?.projectId)
            )
        }
}