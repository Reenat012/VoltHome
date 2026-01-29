package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.mugalimov.volthome.data.local.entity.DeviceEntityExt

@Dao
interface DeviceDaoExt {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg items: DeviceEntityExt)

    @Query("UPDATE devices_ext SET is_deleted=1, updated_at=:updatedAt WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>, updatedAt: String)

    @Query("SELECT * FROM devices_ext WHERE project_id=:projectId AND updated_at > :since")
    suspend fun changedSince(projectId: String, since: String): List<DeviceEntityExt>
}