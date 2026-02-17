package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices

/**
 * DTO для whitelist-обновлений derived-полей.
 * ВАЖНО: сюда добавляем ТОЛЬКО derived-колонки.
 * Никаких phase/groupNumber/order/groupType/roomId и т.п.
 */
data class GroupNominalCurrentUpdate(
    val groupId: Long,
    val nominalCurrent: Double
)

@Dao
interface GroupDao {

    // -------------------- OBSERVE --------------------

    @Transaction
    @Query("SELECT * FROM `groups` WHERE project_id = :projectId")
    fun observeGroupsWithDevicesByProject(projectId: String): Flow<List<CircuitGroupWithDevices>>

    @Query("SELECT * FROM `groups` WHERE project_id = :projectId")
    fun observeAllGroupsByProject(projectId: String): Flow<List<CircuitGroupEntity>>

    @Transaction
    @Query("SELECT * FROM `groups`")
    fun observeGroupsWithDevices(): Flow<List<CircuitGroupWithDevices>>

    @Query("SELECT * FROM `groups`")
    fun observeAllGroups(): Flow<List<CircuitGroupEntity>>

    // -------------------- READ (dbState для diff-commit) --------------------

    /**
     * Снимок групп конкретного проекта.
     * Используется для расчёта diff (dbState vs desiredState).
     */
    @Query("SELECT * FROM `groups` WHERE project_id = :projectId")
    suspend fun getAllGroupsByProject(projectId: String): List<CircuitGroupEntity>

    /**
     * Снимок групп по списку groupId (доп. хелпер под точечные проверки/санити).
     * Важно: projectId boundary встроен в запрос.
     */
    @Query(
        """
        SELECT * FROM `groups`
        WHERE project_id = :projectId
          AND group_id IN (:groupIds)
        """
    )
    suspend fun getGroupsByIds(projectId: String, groupIds: List<Long>): List<CircuitGroupEntity>

    @Query("SELECT * FROM `groups`")
    suspend fun getAllGroups(): List<CircuitGroupEntity>

    // -------------------- INSERT --------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addGroup(group: CircuitGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<CircuitGroupEntity>): List<Long>

    // -------------------- UPDATE (без REPLACE, чтобы не сносить joins каскадом) --------------------

    /**
     * Критично для diff-commit:
     * @Update делает UPDATE без delete/insert, значит НЕ триггерит каскад удаления join.
     */
    @Update
    suspend fun updateGroups(groups: List<CircuitGroupEntity>)

    // -------------------- LOOKUPS --------------------

    @Query("SELECT * FROM `groups` WHERE group_id = :id")
    suspend fun getGroupById(id: Long): CircuitGroupEntity?

    @Query("SELECT * FROM `groups` WHERE room_name = :roomName")
    suspend fun getGroupByRoom(roomName: String): List<CircuitGroupEntity>

    /**
     * Важно: в БД group_type — String.
     */
    @Query("SELECT * FROM `groups` WHERE group_type = :groupType")
    suspend fun getGroupByType(groupType: String): List<CircuitGroupEntity>

    // -------------------- DELETE --------------------

    @Query("DELETE FROM `groups` WHERE room_id = :roomId")
    suspend fun deleteGroupByRoomId(roomId: Long)

    @Query("DELETE FROM `groups` WHERE group_id = :groupId")
    suspend fun deleteGroupByGroupId(groupId: Long)

    /**
     * Удаление групп по списку ids строго в рамках projectId.
     * Это и есть "groupsToDelete" из диффа.
     */
    @Query(
        """
        DELETE FROM `groups`
        WHERE project_id = :projectId
          AND group_id IN (:groupIds)
        """
    )
    suspend fun deleteGroupsByIds(projectId: String, groupIds: List<Long>): Int

    /**
     * ❌ ОПАСНО: удаляет группы всех проектов.
     * Использовать только для dev/тестов/сброса БД в отладочных сценариях.
     */
    @Deprecated(
        message = "ОПАСНО: удаляет группы всех проектов. Используйте deleteAllGroupsByProject(projectId).",
        replaceWith = ReplaceWith("deleteAllGroupsByProject(projectId)"),
        level = DeprecationLevel.ERROR
    )
    @Query("DELETE FROM `groups`")
    suspend fun deleteAllGroups()

    // -------------------- MISC --------------------

    @Deprecated(
        message = "ОПАСНО: нет project boundary. Используйте updateDerivedNominalCurrentOnly(projectId, ...).",
        replaceWith = ReplaceWith("updateDerivedNominalCurrentOnly(projectId, groupId, current, epsilon)"),
        level = DeprecationLevel.ERROR
    )
    @Query("UPDATE `groups` SET nominal_current = :current WHERE group_id = :groupId")
    suspend fun updateGroupCurrent(groupId: Long, current: Double)

    @Transaction
    @Query("SELECT * FROM `groups`")
    suspend fun getAllGroupsWithDevices(): List<CircuitGroupWithDevices>

    @Transaction
    @Query("SELECT * FROM `groups` WHERE group_id = :groupId")
    suspend fun getGroupWithDevicesById(groupId: Long): CircuitGroupWithDevices?

    // ------- для Sync/проектных операций -------

    @Query("SELECT COUNT(*) FROM `groups` WHERE project_id = :projectId")
    suspend fun countByProjectId(projectId: String): Int

    @Query("UPDATE `groups` SET project_id = :newId WHERE project_id = :oldId")
    suspend fun rebindProjectGroups(oldId: String, newId: String): Int

    @Query("DELETE FROM `groups` WHERE project_id = :projectId")
    suspend fun deleteGroupsByProject(projectId: String): Int

    /**
     * Возвращает id всех групп конкретного проекта.
     */
    @Query("SELECT group_id FROM `groups` WHERE project_id = :projectId")
    suspend fun getGroupIdsByProject(projectId: String): List<Long>

    /**
     * Удаляет все группы только в рамках конкретного проекта.
     * (нужно для AUTO replace, но не для manual diff)
     */
    @Query("DELETE FROM `groups` WHERE project_id = :projectId")
    suspend fun deleteAllGroupsByProject(projectId: String)

// -------------------- DERIVED WHITELIST UPDATES --------------------

    /**
     * ✅ Whitelist-апдейт derived-поля nominal_current:
     * - строго по project_id + group_id (никаких cross-project)
     * - идемпотентность: обновляем только если значение реально изменилось
     *
     * @return rowsUpdated: 1 если обновили, 0 если значение уже было таким же (нормально)
     */
    @Query(
        """
    UPDATE `groups`
    SET nominal_current = :nominalCurrent
    WHERE project_id = :projectId
      AND group_id = :groupId
      AND (
          nominal_current IS NULL
          OR ABS(nominal_current - :nominalCurrent) > :epsilon
      )
    """
    )
    suspend fun updateDerivedNominalCurrentOnly(
        projectId: String,
        groupId: Long,
        nominalCurrent: Double,
        epsilon: Double
    ): Int

    /**
     * Batch-обновление derived-полей (whitelist).
     *
     * Правила:
     * - только derived (nominal_current)
     * - project boundary обязателен
     * - идемпотентность через epsilon
     * - защита от NaN/Infinity (иначе ABS/сравнения становятся мусором)
     * - chunking, чтобы не держать write-транзакцию слишком долго на больших списках
     *
     * @return total rowsUpdated
     */
    @Transaction
    suspend fun updateDerivedNominalCurrentOnlyBatch(
        projectId: String,
        updates: List<GroupNominalCurrentUpdate>,
        epsilon: Double,
        chunkSize: Int = 200
    ): Int {
        if (updates.isEmpty()) return 0

        // ❗SQLite/Room + NaN/Infinity = потенциальная грязь в БД и сломанная идемпотентность.
        // Сразу фильтруем.
        val safe = updates
            .asSequence()
            .filter { it.groupId > 0L }
            .filter { it.nominalCurrent.isFinite() } // kotlin Double.isFinite()
            .toList()

        if (safe.isEmpty()) return 0

        var total = 0

        // Дробим на чанки: меньше удержание write-lock, меньше шанс фризов и конкуренции с другими write-операциями.
        var i = 0
        while (i < safe.size) {
            val end = minOf(i + chunkSize, safe.size)
            val chunk = safe.subList(i, end)

            for (u in chunk) {
                total += updateDerivedNominalCurrentOnly(
                    projectId = projectId,
                    groupId = u.groupId,
                    nominalCurrent = u.nominalCurrent,
                    epsilon = epsilon
                )
            }

            i = end
        }

        return total
    }
}