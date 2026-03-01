package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.domain.model.GroupingResult

/**
 * Commit 3 (v3):
 * AUTO rebuild после add-device.
 *
 * A) insertedIds.isEmpty() -> SKIP
 * B) manualActive(pid) -> SUPPRESS
 * C) full rebuild через calculator + SaveAuto...
 * D) single-flight BEGIN/END с opId
 *
 * Commit 4 (BASTION):
 * - прокидываем source/opId в SaveAutoCalculatedGroupsToLocalDbUseCase,
 *   чтобы "AUTO_SAVE SUPPRESS" коррелировался с opId и source.
 *
 * ВАЖНО:
 * - mismatch activeProjectId НЕ SKIP
 * - временное переключение activeProjectId происходит ВНЕ writer-блока
 */
class AutoRebuildGroupsAfterDeviceInsertUseCase @Inject constructor(
    private val activeProjectDs: ActiveProjectDataStore,
    private val manualRepo: ManualEditSessionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val calculatorFactory: GroupCalculatorFactory,
    private val saveAutoUseCase: SaveAutoCalculatedGroupsToLocalDbUseCase,
    private val coordinator: StructuralWriteCoordinator
) {

    data class Params(
        val projectIdRecorded: String,
        val insertedIds: List<Long>,
        val opId: String
    )

    suspend fun execute(params: Params) {
        val pid = params.projectIdRecorded.trim()
        if (pid.isBlank()) return

        // A) Guard
        if (params.insertedIds.isEmpty()) {
            Log.i("AUTO_POST_INSERT", "SKIP emptyIds pid=$pid opId=${params.opId}")
            return
        }

        // B) Suppress manual (in-memory)
        if (manualRepo.isManualActive(pid)) {
            Log.i("AUTO_POST_INSERT", "SUPPRESS manualActive pid=$pid opId=${params.opId}")
            return
        }

        val mode = preferencesRepository.phaseMode.first()

        val previousActivePid = activeProjectDs.activeProjectId.first().orEmpty().trim()
        val needSwitch = previousActivePid != pid

        if (needSwitch) {
            Log.w(
                "AUTO_POST_INSERT",
                "ACTIVE_PID_MISMATCH switching activeProjectId from=$previousActivePid to=$pid opId=${params.opId}"
            )
            activeProjectDs.setActiveProjectId(pid)
        }

        try {
            val out = coordinator.execute(
                projectId = pid,
                opName = "DEVICE_CREATED_AUTO_REBUILD",
                meta = StructuralWriteCoordinator.Meta(
                    reason = "DEVICE_CREATED_AUTO_REBUILD",
                    source = "RoomRepositoryImpl.addDevicesToRoom",
                    opId = params.opId
                )
            ) {
                // 🔒 Внутри writer-блока — только чистая структура
                val result = calculatorFactory.create().calculateGroups(mode)

                when (result) {
                    is GroupingResult.Success -> {
                        // ✅ Commit 4: source/opId прокинуты до AUTO_SAVE
                        saveAutoUseCase.execute(
                            SaveAutoCalculatedGroupsToLocalDbUseCase.Params(
                                projectId = pid,
                                groups = result.system.groups,
                                distributionDecisions = result.distributionDecisions,
                                source = "RoomRepositoryImpl.addDevicesToRoom",
                                opId = params.opId
                            )
                        )
                        Log.i(
                            "AUTO_POST_INSERT",
                            "DONE pid=$pid opId=${params.opId} groups=${result.system.groups.size}"
                        )
                    }

                    is GroupingResult.Error -> {
                        Log.e(
                            "AUTO_POST_INSERT",
                            "FAILED_CALC pid=$pid opId=${params.opId} msg=${result.message}"
                        )
                    }
                }
            }

            when (out) {
                is StructuralWriteCoordinator.Outcome.Success -> Unit
                StructuralWriteCoordinator.Outcome.Busy ->
                    Log.w("AUTO_POST_INSERT", "BUSY pid=$pid opId=${params.opId}")
                StructuralWriteCoordinator.Outcome.Panic ->
                    Log.e("AUTO_POST_INSERT", "PANIC pid=$pid opId=${params.opId}")
                is StructuralWriteCoordinator.Outcome.Error ->
                    Log.e("AUTO_POST_INSERT", "ERROR pid=$pid opId=${params.opId}", out.throwable)
            }
        } finally {
            if (needSwitch) {
                runCatching {
                    activeProjectDs.setActiveProjectId(previousActivePid)
                }.onFailure {
                    Log.e(
                        "AUTO_POST_INSERT",
                        "ACTIVE_PID_RESTORE_FAILED previous=$previousActivePid opId=${params.opId}",
                        it
                    )
                }.onSuccess {
                    Log.w(
                        "AUTO_POST_INSERT",
                        "ACTIVE_PID_RESTORE restored=$previousActivePid opId=${params.opId}"
                    )
                }
            }
        }
    }
}