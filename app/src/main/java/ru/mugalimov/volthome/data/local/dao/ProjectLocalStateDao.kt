package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity

/**
 * DAO локального состояния проекта.
 *
 * Commit 1:
 * - persisted marker active_manual_project_id
 * - ensureRow через @Insert(IGNORE)
 * - DAO как abstract class (Room/KAPT без сюрпризов)
 */
@Dao
abstract class ProjectLocalStateDao {


    @Query("SELECT * FROM project_local_state WHERE project_id=:projectId LIMIT 1")
    abstract suspend fun get(projectId: String): ProjectLocalStateEntity?


    /**
     * Upsert для ProjectLocalStateEntity:
     * - INSERT если нет
     * - REPLACE если есть (по PK project_id)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(state: ProjectLocalStateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(state: ProjectLocalStateEntity): Long

    /**
     * Гарантирует существование строки проекта. Если строки нет — создаёт.
     * Важно: используем РЕАЛЬНЫЕ имена полей сущности (snake_case).
     */
    suspend fun ensureRow(projectId: String) {
        insertIgnore(
            ProjectLocalStateEntity(
                project_id = projectId,
                remote_version = 0,
                last_sync_at = null,
                has_local_changes = false,
                active_manual_project_id = null
            )
        )
    }

    /**
     * Ставим marker в строке проекта: active_manual_project_id = projectId.
     *
     * Важно: перед этим должен быть ensureRow(projectId).
     * @return rowsUpdated (в Commit 1 ожидаем ровно 1)
     */
    @Query("""
        UPDATE project_local_state
        SET active_manual_project_id = :projectId
        WHERE project_id = :projectId
    """)
    abstract suspend fun setActiveManualMarker(projectId: String): Int

    /**
     * Точечная очистка marker в строке проекта.
     * @return rowsUpdated
     */
    @Query("""
        UPDATE project_local_state
        SET active_manual_project_id = NULL
        WHERE project_id = :projectId
    """)
    abstract suspend fun clearActiveManualMarker(projectId: String): Int

    /**
     * Глобальный запрос: какой проект отмечен как manual.
     */
    @Query("""
        SELECT active_manual_project_id
        FROM project_local_state
        WHERE active_manual_project_id IS NOT NULL
        LIMIT 1
    """)
    abstract suspend fun getActiveManualProjectId(): String?

    /**
     * Аварийный рубильник: снимаем marker во всех строках.
     * Используется для reconciliation и для атомарного single-marker set.
     * @return rowsUpdated
     */
    @Query("""
        UPDATE project_local_state
        SET active_manual_project_id = NULL
        WHERE active_manual_project_id IS NOT NULL
    """)
    abstract suspend fun clearActiveManualMarkerGlobal(): Int

    @Query("UPDATE project_local_state SET remote_version=:remoteVersion WHERE project_id=:projectId")
    abstract suspend fun updateRemoteVersion(projectId: String, remoteVersion: Int)

    @Query("UPDATE project_local_state SET last_sync_at=:ts WHERE project_id=:projectId")
    abstract suspend fun updateLastSyncAt(projectId: String, ts: String?)

    @Query("UPDATE project_local_state SET has_local_changes=:flag WHERE project_id=:projectId")
    abstract suspend fun setHasLocalChanges(projectId: String, flag: Boolean)

    @Query("SELECT * FROM project_local_state")
    abstract fun observeAll(): Flow<List<ProjectLocalStateEntity>>

    @Query("DELETE FROM project_local_state WHERE project_id = :projectId")
    abstract suspend fun delete(projectId: String)
}