package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity

/**
 * DAO локального состояния проекта.
 *
 * Commit 1:
 * - persisted marker active_manual_project_id
 * - ensureRow через @Insert(IGNORE)
 * - DAO как abstract class (Room/KAPT без сюрпризов)
 *
 * Commit X (manual ownership):
 * - persisted флаг manual_overrides_present (SoT lock)
 * - persisted версия manual_lock_bootstrap_version (bootstrap done)
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
    @Upsert
    abstract suspend fun upsert(state: ProjectLocalStateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(state: ProjectLocalStateEntity): Long

    /**
     * Гарантирует существование строки проекта. Если строки нет — создаёт.
     *
     * ВАЖНО:
     * - projectId может прилететь с пробелами.
     * - пустой projectId нельзя вставлять в таблицу (это будет мусорная строка).
     */
    suspend fun ensureRow(projectId: String) {
        val pid = projectId.trim()
        if (pid.isBlank()) return

        insertIgnore(
            ProjectLocalStateEntity(
                project_id = pid,
                active_manual_project_id = null,
                manual_overrides_present = false,
                manual_lock_bootstrap_version = 0
            )
        )
    }

    // -------------------------
    // Manual session marker (active manual)
    // -------------------------

    /**
     * Ставим marker в строке проекта: active_manual_project_id = projectId.
     *
     * Важно: перед этим должен быть ensureRow(projectId).
     * @return rowsUpdated (в Commit 1 ожидаем ровно 1)
     */
    @Query(
        """
        UPDATE project_local_state
        SET active_manual_project_id = :projectId
        WHERE project_id = :projectId
        """
    )
    abstract suspend fun setActiveManualMarker(projectId: String): Int

    /**
     * Точечная очистка marker в строке проекта.
     * @return rowsUpdated
     */
    @Query(
        """
        UPDATE project_local_state
        SET active_manual_project_id = NULL
        WHERE project_id = :projectId
        """
    )
    abstract suspend fun clearActiveManualMarker(projectId: String): Int

    /**
     * Глобальный запрос: какой проект отмечен как manual.
     */
    @Query(
        """
        SELECT active_manual_project_id
        FROM project_local_state
        WHERE active_manual_project_id IS NOT NULL
        LIMIT 1
        """
    )
    abstract suspend fun getActiveManualProjectId(): String?

    /**
     * Аварийный рубильник: снимаем marker во всех строках.
     * Используется для reconciliation и для атомарного single-marker set.
     * @return rowsUpdated
     */
    @Query(
        """
        UPDATE project_local_state
        SET active_manual_project_id = NULL
        WHERE active_manual_project_id IS NOT NULL
        """
    )
    abstract suspend fun clearActiveManualMarkerGlobal(): Int

    // -------------------------
    // Manual ownership lock (persisted)
    // -------------------------

    /**
     * Возвращает persisted ownership-флаг:
     * true -> AUTO не имеет права писать структуру групп.
     *
     * Важно: перед чтением должна быть ensureRow(projectId) на контуре входа в проект.
     */
    @Query(
        """
        SELECT manual_overrides_present
        FROM project_local_state
        WHERE project_id = :projectId
        LIMIT 1
        """
    )
    abstract suspend fun getManualOverridesPresent(projectId: String): Boolean?

    /**
     * Ставит persisted ownership-флаг.
     * @return rowsUpdated (ожидаем 1 при корректном ensureRow)
     */
    @Query(
        """
        UPDATE project_local_state
        SET manual_overrides_present = :flag
        WHERE project_id = :projectId
        """
    )
    abstract suspend fun setManualOverridesPresent(projectId: String, flag: Boolean): Int

    /**
     * Наблюдение за ownership-флагом (для UI "Сбросить ручные изменения").
     */
    @Query("SELECT manual_overrides_present FROM project_local_state WHERE project_id=:projectId LIMIT 1")
    abstract fun observeManualOverridesPresent(projectId: String): Flow<Boolean?>

    // -------------------------
    // Bootstrap version (persisted)
    // -------------------------

    /**
     * Версия bootstrap/backfill (0 = не делали).
     */
    @Query(
        """
        SELECT manual_lock_bootstrap_version
        FROM project_local_state
        WHERE project_id = :projectId
        LIMIT 1
        """
    )
    abstract suspend fun getManualLockBootstrapVersion(projectId: String): Int?

    /**
     * Ставит версию bootstrap/backfill.
     * @return rowsUpdated
     */
    @Query(
        """
        UPDATE project_local_state
        SET manual_lock_bootstrap_version = :version
        WHERE project_id = :projectId
        """
    )
    abstract suspend fun setManualLockBootstrapVersion(projectId: String, version: Int): Int

    @Query("SELECT * FROM project_local_state")
    abstract fun observeAll(): Flow<List<ProjectLocalStateEntity>>

    @Query("DELETE FROM project_local_state WHERE project_id = :projectId")
    abstract suspend fun delete(projectId: String)
}
