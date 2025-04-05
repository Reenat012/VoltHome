package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.domain.model.Device

@Dao
interface RoomDao {
    //получение потока данных со списком комнат и сортировкой по дате создания
    //возвращает flow для автоматического обновления при изменениях в БД
    @Query("SELECT * FROM rooms ORDER BY created_at DESC")
    fun observeAllRooms(): Flow<List<RoomEntity>>

    //добавление новой комнаты
    //onConflict = OnConflictStrategy.ABORT - если запись с таким же PrimeryKey существует
    //то запись прервывается
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addRoom(room: RoomEntity) : Long // Возвращаем ID новой комнаты

    //удаление комнаты по id
    //возращает количество удаленных строк 0 или 1
    @Query("DELETE FROM rooms WHERE id = :roomId")
    suspend fun deleteRoomById(roomId: Long) : Int

    //проверить существует ли комната с таким именем
    @Query("SELECT EXISTS(SELECT 1 FROM rooms WHERE name = :name LIMIT 1)")
    suspend fun existsByName(name: String): Boolean

    //получить комнату по roomId
    @Query("SELECT * FROM rooms WHERE id = :roomId")
    suspend fun getRoomById(roomId: Long): RoomEntity?
}