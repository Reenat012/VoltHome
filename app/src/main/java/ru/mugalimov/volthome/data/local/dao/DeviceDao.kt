package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.DeviceEntity

@Dao
interface DeviceDao {
    //получение потока данных со списком устройств и сортировкой по дате создания
    //возвращает flow для автоматического обновления при изменениях в БД
    @Query("SELECT * FROM devices WHERE room_id = :roomId")
    fun observeDevicesByIdRoom(roomId: Long): Flow<List<DeviceEntity>>

    //добавить устройство в комнату
    //onConflict = OnConflictStrategy.ABORT - если запись с таким же PrimeryKey существует
    //то запись прервывается
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addDevice(deviceEntity: DeviceEntity)

    //удаление устройства по id
    //возращает количество удаленных строк 0 или 1
    @Query("DELETE FROM devices WHERE device_id = :deviceId")
    suspend fun deleteDeviceById(deviceId: Long): Int

    //получить устройство по deviceId
    @Query("SELECT * FROM devices WHERE device_id = :deviceId")
    suspend fun getDeviceById(deviceId: Int): DeviceEntity?

    // Получить все устройства с определенной комнаты по id комнаты
    @Query("SELECT * FROM devices WHERE room_id = :roomId")
    suspend fun getAllDevicesByRoomId(roomId: Long): List<DeviceEntity>

    @Query("SELECT * FROM devices")
    suspend fun getAllDevices() : List<DeviceEntity>

    @Query("""
    SELECT * FROM devices 
    WHERE device_id IN (
        SELECT device_id FROM group_device_join 
        WHERE group_id = :groupId
    )
""")
    suspend fun getDevicesForGroup(groupId: Long): List<DeviceEntity>
}