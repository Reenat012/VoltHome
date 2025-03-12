package ru.mugalimov.volthome.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.entity.DeviceEntity

@Dao
interface DeviceDao {
    //получение потока данных со списком устройств и сортировкой по дате создания
    //возвращает flow для автоматического обновления при изменениях в БД
    @Query("SELECT * FROM devices ORDER BY created_at DESC")
    fun observeAllDevice(): Flow<List<DeviceEntity>>

    //добавление новой комнаты
    //onConflict = OnConflictStrategy.ABORT - если запись с таким же PrimeryKey существует
    //то запись прервывается
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addDevices(device: DeviceEntity)

    //удаление устройства по id
    //возращает количество удаленных строк 0 или 1
    @Query("DELETE FROM devices WHERE id = :deviceId")
    suspend fun deleteDeviceById(deviceId: Int): Int

    //проверить существует ли устройство с таким именем
    @Query("SELECT EXISTS(SELECT 1 FROM devices WHERE name = :name LIMIT 1)")
    suspend fun existsByName(name: String): Boolean

    //получить устройство по deviceId
    @Query("SELECT * FROM devices WHERE id = :deviceId")
    suspend fun getDeviceById(deviceId: Int): DeviceEntity?
}