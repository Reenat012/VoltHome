package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
import ru.mugalimov.volthome.domain.model.phase_load.GroupWithDevices

interface ExplicationRepository {

    /**
     * ## Membership (устройство ↔ группа)
     *
     * **Источник истины:** `group_device_join`.
     *
     * В текущей схеме **у устройства нет поля `groupId`** ни в `DeviceEntity`, ни в доменной модели `Device`.
     * Поэтому принадлежность устройства группе задаётся **только** через join-таблицу.
     *
     * ### Матрица чтения/записи (факт по коду)
     * - **Чтение (AUTO / UI):**
     *   - `GroupDao.observeGroupsWithDevicesByProject(projectId)` возвращает `CircuitGroupWithDevices`
     *     через Room `@Relation` + `@Junction(GroupDeviceJoin)`.
     * - **Чтение (Manual baseState):**
     *   - `getGroupsWithDevicesByProject(projectId)` читает группы из `groups` и устройства по join
     *     (`GroupDeviceJoinDao.getDevicesForGroup(groupId)`), после чего строит baseState.
     * - **Запись (перенос/сохранение состава):**
     *   - любые изменения membership должны выражаться как операции над `group_device_join`
     *     (insert/delete связей).
     *
     * ### Важные ограничения
     * - `group_device_join` **не содержит `projectId`**, поэтому все операции удаления/обновления
     *   должны быть **строго ограничены** группами, принадлежащими конкретному `projectId`.
     */

    fun observeDistributionDecisions(): Flow<List<DistributionDecision>>
    suspend fun setLastDistributionDecisions(decisions: List<DistributionDecision>)

    fun observeAllGroup(): Flow<List<CircuitGroup>>

    fun observeGroupsWithDevices(): Flow<List<ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices>>

    /**
     * ## Manual Save (v1.1): DIFF-COMMIT контракт (Вариант B / идеальный)
     *
     * Источник истины при сохранении — `draftState` (группы + membership + unassigned).
     *
     * ### Что должен сделать репозиторий (контракт)
     * - Сравнить `dbState(projectId)` (groups + joins) и `desiredState(draftState)`.
     * - Выполнить **только дифф-операции**:
     *   - `update` существующих групп (ВАЖНО: `group_id` сохраняется),
     *   - `insert` новых групп (получают новый `group_id`),
     *   - `delete` удалённых групп (каскадно: сначала joins → потом groups),
     *   - membership фиксируется операциями над `group_device_join`.
     * - `unassignedDeviceIds` в draft — это **виртуальный контейнер**:
     *   - после Save в БД **не должно** остаться join-записей для этих устройств.
     *
     * ### Порядок транзакции (обязателен)
     * 1) загрузить dbGroups/dbJoins (строго по projectId),
     * 2) посчитать diff,
     * 3) применить удаления (joins → groups),
     * 4) применить insert/update групп,
     * 5) применить joins,
     * 6) sanity-check (dbState == desiredState), иначе ошибка.
     *
     * ### Границы и запреты
     * - Всё строго в рамках `projectId`.
     * - В manual Save **запрещено** использовать `replaceAllGroupsTransactional`.
     * - При ошибке Save **не должен** выключать manual.
     *
     * ### После Save (политика, закрепляем контрактом)
     * - Можно чистить override-таблицы, которые способны "накрыть" сохранённую структуру (например phase overrides),
     *   но нельзя запускать полный auto recalc, который перетрёт ручную структуру.
     */
    suspend fun commitManualDraftTransactional(
        projectId: String,
        draftState: ProjectEditState
    )

    /**
     * AUTO-путь (исторический): replace-by-delete+insert.
     * В manual Save этот метод использовать нельзя.
     */
    /**
     * ✅ Обычный вход: сам берёт structural lock.
     */
    suspend fun replaceAllGroupsTransactional(projectId: String, groups: List<CircuitGroup>)

    /**
     * ⚠️ Вызов ТОЛЬКО если ВНЕ уже есть structuralWriteMutex.withLock(projectId).
     * Нужен, чтобы избежать self-deadlock (Mutex не реентерабелен).
     */
    suspend fun replaceAllGroupsTransactionalAlreadyLocked(projectId: String, groups: List<CircuitGroup>)
    suspend fun replaceAllGroupsTransactional(groups: List<CircuitGroup>)

    /**
     * ✅ Явный project-scoped поток групп (VM-правильный вариант).
     *
     * Зачем:
     * - чтобы ViewModel могла жёстко привязать чтение к конкретному projectId
     *   и не зависеть от того, что репо "само" внутри читает activeProjectId.
     * - чтобы при смене проекта upstream гарантированно пересоздавался
     *   (flatMapLatest в VM) и не было "эхо" старых значений.
     */
    fun observeAllGroupByProject(projectId: String): Flow<List<CircuitGroup>>

    suspend fun getGroupsWithDevices(): List<GroupWithDevices>
    suspend fun getGroupsWithDevicesByProject(projectId: String): List<GroupWithDevices>

    suspend fun addGroup(circuitGroups: List<CircuitGroup>)
    suspend fun updateGroup(groupId: Long)

    suspend fun getAllGroups(): List<CircuitGroup>

    suspend fun deleteAllGroups()

    suspend fun addDeviceToGroup(deviceId: Long, groupId: Long)

    suspend fun getDevicesForGroup(groupId: Long): List<Device>

    suspend fun handleRoomDeletion(roomId: Long)
    suspend fun handleDeviceDeletion(deviceId: Long)

    suspend fun getGroupById(groupId: Long): CircuitGroup?

    suspend fun getGroupByRoom(roomName: String): List<CircuitGroup>
    suspend fun getGroupByType(groupType: ru.mugalimov.volthome.domain.model.DeviceType): List<CircuitGroup>
}