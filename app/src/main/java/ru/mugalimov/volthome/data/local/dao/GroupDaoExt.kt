package ru.mugalimov.volthome.data.local.dao

import androidx.room.*
import ru.mugalimov.volthome.data.local.entity.GroupEntityExt

@Dao
interface GroupDaoExt {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg items: GroupEntityExt)

    @Query("UPDATE groups SET is_deleted=1, updated_at=:updatedAt WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>, updatedAt: String)

    @Query("SELECT * FROM groups WHERE project_id=:projectId AND updated_at > :since")
    suspend fun changedSince(projectId: String, since: String): List<GroupEntityExt>
}