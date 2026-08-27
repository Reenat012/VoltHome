package ru.mugalimov.volthome.data.repository.impl

import android.util.Log
import android.content.Context
import androidx.room.withTransaction
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
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.domain.mapper.mapToDomainDevices
import ru.mugalimov.volthome.domain.mapper.toDomainDevice
import ru.mugalimov.volthome.domain.mapper.toEntityDevice
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.ui.components.JsonParser
import java.util.Date
import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository

class DeviceRepositoryImpl @Inject constructor(
    private val deviceDao: DeviceDao,
    private val database: AppDatabase,
    private val roomDao: RoomDao,
    private val explicationRepository: ExplicationRepository,
    private val manualEditSessionRepository: ManualEditSessionRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatchers: CoroutineDispatcher
) : DeviceRepository {

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

                // 2) Локальная вставка
                val createdAt = Date()
                deviceDao.addDevice(
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

            val pid = current.projectId

            val updatedDevice = device.copy(
                roomId = device.roomId ?: current.roomId
            )

            // 1) локально
            deviceDao.update(
                updatedDevice.toEntityDevice(pid)
            )

            // 2) синхронизация active manual draft
            manualEditSessionRepository.syncEditedDeviceInManualSession(
                projectId = pid,
                device = updatedDevice
            )

            Log.i(
                "DEVICE_UPDATE_SYNC",
                "pid=$pid deviceId=${updatedDevice.id} power=${updatedDevice.power} " +
                        "pf=${updatedDevice.powerFactor} dr=${updatedDevice.demandRatio} " +
                        "voltage=${updatedDevice.voltage.value}/${updatedDevice.voltage.type}"
            )
            Unit
        }

    // --------------------------- Delete (single) -------------------

    override suspend fun deleteDevice(deviceId: Long) {
        withContext(dispatchers) {
            if (deviceDao.getDeviceById(deviceId.toInt()) == null) {
                throw DeviceNotFoundException("Устройство с ID $deviceId не найдено")
            }

            database.withTransaction {
                // Сначала обновляем зависимые группы, пока связи ещё доступны.
                // Любая ошибка откатывает всю операцию.
                explicationRepository.handleDeviceDeletion(deviceId)
                val rowsDeleted = deviceDao.deleteDeviceById(deviceId)
                if (rowsDeleted == 0) {
                    throw DeviceNotFoundException("Устройство с ID $deviceId не найдено")
                }
            }
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

    override suspend fun getDevicesByIds(ids: List<Long>): List<Device> =
        withContext(dispatchers) {
            if (ids.isEmpty()) return@withContext emptyList()

            // 1) Одним запросом получаем сущности (tombstones уже отфильтрованы на уровне DAO)
            val entities = deviceDao.getDevicesByIds(ids)

            // 2) Делаем индекс по id
            val byId = entities.associateBy { it.deviceId }

            // 3) Детерминированность: возвращаем в том же порядке, что входные ids.
            // Если каких-то id нет (удалены/tombstone/не существуют) — просто пропускаем.
            ids.distinct().mapNotNull { id -> byId[id]?.toDomainDevice() }
        }
}
