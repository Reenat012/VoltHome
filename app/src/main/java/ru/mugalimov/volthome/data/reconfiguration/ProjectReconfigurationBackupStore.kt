package ru.mugalimov.volthome.data.reconfiguration

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.data.local.entity.ApparatusSelectionEntity
import ru.mugalimov.volthome.data.local.entity.CableLineCalculationEntity
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.data.local.entity.GroupPhaseOverrideEntity
import ru.mugalimov.volthome.data.local.entity.PanelLayoutEntity
import ru.mugalimov.volthome.data.local.entity.ProjectCableDefaultsEntity
import ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity
import ru.mugalimov.volthome.data.local.entity.ProjectSetupEntity
import ru.mugalimov.volthome.di.database.AppDatabase

/**
 * Один устойчивый снимок до последней переконфигурации.
 *
 * Он хранится отдельно от Room: миграция схемы не нужна, а отмена переживает
 * перезапуск процесса. Снимок содержит только техническое состояние проекта.
 */
@Singleton
class ProjectReconfigurationBackupStore @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
    private val db: AppDatabase
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun capture(projectId: String): Snapshot {
        val snapshot = db.withTransaction {
            val groups = db.groupDao().getAllGroupsByProject(projectId)
            val groupIds = groups.map(CircuitGroupEntity::groupId)
            Snapshot(
                schemaVersion = SNAPSHOT_SCHEMA_VERSION,
                projectId = projectId,
                createdAtEpochMs = System.currentTimeMillis(),
                setup = db.projectSetupDao().get(projectId),
                localState = db.projectLocalStateDao().get(projectId),
                groups = groups,
                joins = if (groupIds.isEmpty()) emptyList()
                    else db.groupDeviceJoinDao().getJoinsForGroupIds(groupIds),
                phaseOverrides = db.groupPhaseOverrideDao().getByProject(projectId),
                apparatusSelections = db.apparatusSelectionDao().getByProject(projectId),
                panelLayout = db.panelLayoutDao().get(projectId),
                cableDefaults = db.cableCalculationDao().getDefaults(projectId),
                cableCalculations = db.cableCalculationDao().getCalculations(projectId)
            )
        }
        save(snapshot)
        return snapshot
    }

    fun hasBackup(projectId: String): Boolean = load(projectId) != null

    fun backupCreatedAt(projectId: String): Long? = load(projectId)?.createdAtEpochMs

    suspend fun restore(projectId: String): Snapshot? {
        val snapshot = load(projectId) ?: return null
        require(snapshot.projectId == projectId) { "Снимок относится к другому проекту" }

        db.withTransaction {
            db.groupPhaseOverrideDao().deleteByProject(projectId)
            db.apparatusSelectionDao().deleteByProject(projectId)
            db.panelLayoutDao().delete(projectId)
            db.cableCalculationDao().deleteCalculations(projectId)
            db.groupDao().deleteAllGroupsByProject(projectId)

            snapshot.setup?.let { db.projectSetupDao().upsert(it) }
            snapshot.localState?.let { db.projectLocalStateDao().upsert(it) }
            snapshot.cableDefaults?.let { db.cableCalculationDao().upsertDefaults(it) }

            if (snapshot.groups.isNotEmpty()) db.groupDao().insertGroups(snapshot.groups)
            if (snapshot.joins.isNotEmpty()) db.groupDeviceJoinDao().insertAll(snapshot.joins)
            if (snapshot.phaseOverrides.isNotEmpty()) {
                db.groupPhaseOverrideDao().upsertAll(snapshot.phaseOverrides)
            }
            if (snapshot.cableCalculations.isNotEmpty()) {
                db.cableCalculationDao().upsertCalculations(snapshot.cableCalculations)
            }
            if (snapshot.apparatusSelections.isNotEmpty()) {
                db.apparatusSelectionDao().upsertAll(snapshot.apparatusSelections)
            }
            snapshot.panelLayout?.let { db.panelLayoutDao().upsert(it) }
        }
        clear(projectId)
        return snapshot
    }

    fun clear(projectId: String) {
        prefs.edit().remove(key(projectId)).apply()
    }

    private fun save(snapshot: Snapshot) {
        check(prefs.edit().putString(key(snapshot.projectId), gson.toJson(snapshot)).commit()) {
            "Не удалось сохранить резервную копию проекта"
        }
    }

    private fun load(projectId: String): Snapshot? {
        val json = prefs.getString(key(projectId), null) ?: return null
        val snapshot = runCatching { gson.fromJson(json, Snapshot::class.java) }.getOrNull()
        if (snapshot == null) {
            clear(projectId)
            return null
        }
        val isSupported = snapshot.schemaVersion == SNAPSHOT_SCHEMA_VERSION
        val isFresh = System.currentTimeMillis() - snapshot.createdAtEpochMs <= BACKUP_TTL_MS
        if (!isSupported || !isFresh) {
            clear(projectId)
            return null
        }
        return snapshot
    }

    data class Snapshot(
        val schemaVersion: Int,
        val projectId: String,
        val createdAtEpochMs: Long,
        val setup: ProjectSetupEntity?,
        val localState: ProjectLocalStateEntity?,
        val groups: List<CircuitGroupEntity>,
        val joins: List<GroupDeviceJoin>,
        val phaseOverrides: List<GroupPhaseOverrideEntity>,
        val apparatusSelections: List<ApparatusSelectionEntity>,
        val panelLayout: PanelLayoutEntity?,
        val cableDefaults: ProjectCableDefaultsEntity?,
        val cableCalculations: List<CableLineCalculationEntity>
    )

    private fun key(projectId: String) = "snapshot.$projectId"

    private companion object {
        const val PREFS_NAME = "project_reconfiguration_backups"
        const val SNAPSHOT_SCHEMA_VERSION = 1
        val BACKUP_TTL_MS: Long = TimeUnit.DAYS.toMillis(14)
    }
}
