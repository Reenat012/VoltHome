package ru.mugalimov.volthome.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.entity.LoadEntity
import ru.mugalimov.volthome.model.Load

@Dao
interface LoadDao {
    //TODO что-то сомневаюсь что нужно связывать по roomId именно наблюдение
    @Query("SELECT * FROM loads ORDER BY created_at DESC")
    fun observeLoads() : Flow<List<LoadEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addLoad(loadEntity: LoadEntity)
}