package ru.mugalimov.volthome.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.RoomDao
import ru.mugalimov.volthome.data.mapper.toEntity
import ru.mugalimov.volthome.data.remote.api.ProjectsApi
import ru.mugalimov.volthome.di.database.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Минимальная версия SyncManager без ProjectDao/ProjectLocalStateDao.
 * Делает только Pull (загрузка с сервера) по доступным DAO.
 * Никаких локальных версий/last_sync_at здесь пока нет — добавим, когда появятся таблицы.
 */
@Singleton
class SyncManager @Inject constructor(
    private val api: ProjectsApi,
    private val db: AppDatabase,
    private val roomDao: RoomDao,
    private val groupDao: GroupDao,
    private val deviceDao: DeviceDao, // пока не используем — нет подходящих методов
) {

    /**
     * Тянем дельту с фиксированной "давней" точки — т.к. нет локального last_sync_at.
     * Применяем только apперты теми методами, что есть в DAO:
     *  - RoomDao.insertAll(IGNORE) с уникальным индексом name — как upsert по name.
     *  - GroupDao.insertGroups(REPLACE)
     *  - Devices пропускаем (добавим, когда пришлёшь сигнатуры DeviceDao для массовых вставок).
     */
    suspend fun syncProject(projectId: String) = withContext(Dispatchers.IO) {
        // нет локального состояния — берем "с начала времён"
        val since = "1970-01-01T00:00:00Z"

        val delta = api.delta(projectId, since)

        // Rooms: insertAll(IGNORE) + UNIQUE(name) ≈ upsert по имени
        if (delta.rooms.upsert.isNotEmpty()) {
            val rooms = delta.rooms.upsert.map { it.toEntity() }
            roomDao.insertAll(rooms)
        }

        // Groups: insertGroups(REPLACE)
        if (delta.groups.upsert.isNotEmpty()) {
            val groups = delta.groups.upsert.map { it.toEntity(roomId = 0L) }
            groupDao.insertGroups(groups)
        }

        // Devices: пропускаем до появления нужных методов в DeviceDao
        // if (delta.devices.upsert.isNotEmpty()) { ... }

        // Удаления (delta.delete) пока не применяем — нет связки UUID (server) -> PK Long (local).
    }
}