package ru.mugalimov.volthome.domain.use_case.manual

import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.data.local.dao.ProjectLocalStateDao
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.use_case.StructuralWriteCoordinator

/**
 * Reconcile persisted marker ручного режима на старте приложения.
 *
 * Зачем:
 * - после kill-process у нас может остаться marker в БД, но session в памяти уже нет
 * - либо marker указывает на один проект, а session активна в другом
 *
 * ВАЖНО:
 * - UI не должен трогать DAO. UI вызывает только этот usecase.
 * - Все операции с marker строго через coordinator по GLOBAL_MARKER_KEY.
 */
class ReconcileManualMarkerOnStartupUseCase @Inject constructor(
    private val coordinator: StructuralWriteCoordinator,
    private val projectLocalStateDao: ProjectLocalStateDao,
    private val manualRepo: ManualEditSessionRepository
) {

    enum class Action { NOOP, RESET, RESTORE }

    suspend fun execute(): Action {
        val out = coordinator.execute(
            projectId = GLOBAL_MARKER_KEY,
            opName = "MANUAL_RECONCILE_STARTUP"
        ) {
            val session = manualRepo.getActiveSession()
            val hasInMemory = session?.manualModeActive == true
            val sessionPid = session?.projectId

            val marked = projectLocalStateDao.getActiveManualProjectId()

            var action = Action.NOOP

            // 1) marker есть, session нет -> чистим marker
            if (marked != null && !hasInMemory) {
                val cleared = projectLocalStateDao.clearActiveManualMarkerGlobal()
                Log.w(
                    "MANUAL_RECON",
                    "op=MANUAL_RECONCILE_STARTUP pid=$GLOBAL_MARKER_KEY case=marker_without_session " +
                            "marked=$marked sessionPid=null clearedRows=$cleared"
                )
                action = Action.RESET
            }
            // 2) mismatch: marker=A, session=B -> приводим marker к sessionPid
            else if (marked != null && hasInMemory && sessionPid != null && marked != sessionPid) {
                val cleared = projectLocalStateDao.clearActiveManualMarkerGlobal()
                projectLocalStateDao.ensureRow(sessionPid)
                val rows = projectLocalStateDao.setActiveManualMarker(sessionPid)
                require(rows == 1) { "Failed to fix marker on startup (rowsUpdated=$rows)" }

                Log.w(
                    "MANUAL_RECON",
                    "op=MANUAL_RECONCILE_STARTUP pid=$GLOBAL_MARKER_KEY case=mismatch " +
                            "marked=$marked sessionPid=$sessionPid clearedRows=$cleared setRows=$rows"
                )
                action = Action.RESTORE
            } else {
                if (marked != null || hasInMemory) {
                    Log.d(
                        "MANUAL_RECON",
                        "op=MANUAL_RECONCILE_STARTUP pid=$GLOBAL_MARKER_KEY case=ok " +
                                "marked=$marked sessionPid=$sessionPid"
                    )
                }
            }

            action
        }

        return when (out) {
            is StructuralWriteCoordinator.Outcome.Success -> out.value
            StructuralWriteCoordinator.Outcome.Busy -> {
                Log.w("MANUAL_RECON", "startup reconcile BUSY")
                Action.NOOP
            }
            StructuralWriteCoordinator.Outcome.Panic -> {
                Log.e("MANUAL_RECON", "startup reconcile PANIC")
                Action.NOOP
            }
            is StructuralWriteCoordinator.Outcome.Error -> {
                Log.e("MANUAL_RECON", "startup reconcile ERROR", out.throwable)
                Action.NOOP
            }
        }
    }

    companion object {
        // ✅ Единый ключ для GLOBAL writer-операций по persisted marker ручного режима.
        private const val GLOBAL_MARKER_KEY = "__GLOBAL_MANUAL_MARKER__"
    }
}