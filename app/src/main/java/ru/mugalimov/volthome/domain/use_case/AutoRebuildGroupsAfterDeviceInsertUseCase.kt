package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.data.repository.resolve
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.create.AutoCalculationResult

/**
 * Commit 5:
 * AUTO rebuild после add-device.
 *
 * Порядок suppress (ранний выход, чтобы НЕ считать зря):
 * 1) insertedIds.isEmpty() -> SKIP
 * 2) manualLock(pid)==true -> SUPPRESS (persisted ownership: ручные правки присутствуют)
 * 3) manualActive(pid)==true -> SUPPRESS (in-memory session: прямо сейчас в ручном режиме)
 *
 * ВАЖНО:
 * - mismatch activeProjectId НЕ SKIP
 * - временное переключение activeProjectId происходит ВНЕ writer-блока
 *
 * Логи для grep/диагностики:
 * - "AUTO_REBUILD SUPPRESS manualLock=true ..."
 * - "AUTO_REBUILD SUPPRESS manualActive=true ..."
 */
class AutoRebuildGroupsAfterDeviceInsertUseCase @Inject constructor(
    private val activeProjectDs: ActiveProjectDataStore,
    private val manualRepo: ManualEditSessionRepository,
    private val ownershipRepo: ProjectOwnershipRepository,
    private val preferencesRepository: PreferencesRepository,
    private val projectSetupRepository: ProjectSetupRepository,
    private val calculatorFactory: GroupCalculatorFactory,
    private val saveAutoUseCase: SaveAutoCalculatedGroupsToLocalDbUseCase,
    private val coordinator: StructuralWriteCoordinator
) {

    companion object {
        // Единый тег, чтобы быстро grep'ать по логам.
        private const val TAG = "AUTO_REBUILD"
        private const val SOURCE = "RoomRepositoryImpl.addDevicesToRoom"
        private const val REASON = "DEVICE_CREATED_AUTO_REBUILD"
        private const val OP_NAME = "DEVICE_CREATED_AUTO_REBUILD"
    }

    data class Params(
        val projectIdRecorded: String,
        val insertedIds: List<Long>,
        val opId: String,
        val phaseModeOverride: PhaseMode? = null,
        val manageActiveProjectContext: Boolean = true,
        /** Явное обновление AUTO-расчёта после смены версии ядра. */
        val forceRecalculation: Boolean = false
    )

    suspend fun execute(params: Params): AutoCalculationResult {
        val pid = params.projectIdRecorded.trim()
        if (pid.isBlank()) return AutoCalculationResult.Failure("projectId не задан")

        // 1) Guard: пустой список -> нечего делать.
        if (params.insertedIds.isEmpty() && !params.forceRecalculation) {
            Log.i(TAG, "SKIP emptyIds pid=$pid opId=${params.opId}")
            return AutoCalculationResult.Skipped("Нет устройств для расчёта")
        }

        // 2) Suppress по persisted lock: ручные overrides уже есть, AUTO не должен трогать структуру.
        // Важно: это не "manualActive", это именно ownership/SoT (персистентный маркер).
        if (ownershipRepo.isManualLock(pid)) {
            Log.i(
                TAG,
                "SUPPRESS manualLock=true pid=$pid opId=${params.opId} inserted=${params.insertedIds.size}"
            )
            return AutoCalculationResult.Skipped("Проект зафиксирован в ручном режиме")
        }

        // 3) Suppress по текущей ручной сессии (in-memory): пользователь прямо сейчас редактирует.
        if (manualRepo.isManualActive(pid)) {
            Log.i(
                TAG,
                "SUPPRESS manualActive=true pid=$pid opId=${params.opId} inserted=${params.insertedIds.size}"
            )
            return AutoCalculationResult.Skipped("Открыта ручная сессия редактирования")
        }

        // Фаза/режим считаем один раз, снаружи writer-блока.
        val mode = params.phaseModeOverride ?: projectSetupRepository.resolve(
            projectId = pid,
            legacyFallbackPhaseMode = preferencesRepository.phaseMode.first()
        ).phaseMode

        // ВАЖНО: rebuild делаем в контексте projectIdRecorded, а не активного проекта UI.
        val previousActivePid = activeProjectDs.activeProjectId.first().orEmpty().trim()
        val needSwitch = params.manageActiveProjectContext && previousActivePid != pid

        if (needSwitch) {
            Log.w(
                TAG,
                "ACTIVE_PID_MISMATCH switching activeProjectId from=$previousActivePid to=$pid opId=${params.opId}"
            )
            activeProjectDs.setActiveProjectId(pid)
        }

        return try {
            val out = coordinator.execute(
                projectId = pid,
                opName = OP_NAME,
                meta = StructuralWriteCoordinator.Meta(
                    reason = REASON,
                    source = SOURCE,
                    opId = params.opId
                )
            ) {
                // 🔒 Внутри writer-блока — только чистая структура (никаких UI-зависимостей).
                // ВАЖНО: считаем строго в recorded projectId
                val result = calculatorFactory.create(pid).calculateGroups(mode)

                when (result) {
                    is GroupingResult.Success -> {
                        // ✅ source/opId прокинуты до AUTO_SAVE (корреляция).
                        saveAutoUseCase.execute(
                            SaveAutoCalculatedGroupsToLocalDbUseCase.Params(
                                projectId = pid,
                                groups = result.system.groups,
                                distributionDecisions = result.distributionDecisions,
                                source = SOURCE,
                                opId = params.opId
                            )
                        )
                        Log.i(TAG, "DONE pid=$pid opId=${params.opId} groups=${result.system.groups.size}")
                        AutoCalculationResult.Success(
                            linesCount = result.system.groups.size,
                            installedPowerW = result.system.groups.sumOf { it.installedPowerW.toDouble() },
                            calculatedPowerW = result.system.groups.sumOf { group ->
                                CircuitLoadCalculator.calculate(group.devices).calculatedPowerW
                            }
                        )
                    }

                    is GroupingResult.Error -> {
                        Log.e(TAG, "FAILED_CALC pid=$pid opId=${params.opId} msg=${result.message}")
                        AutoCalculationResult.Failure(result.message)
                    }
                }
            }

            when (out) {
                is StructuralWriteCoordinator.Outcome.Success -> out.value
                StructuralWriteCoordinator.Outcome.Busy -> {
                    Log.w(TAG, "BUSY pid=$pid opId=${params.opId}")
                    AutoCalculationResult.Failure("Расчёт уже выполняется. Повторите попытку")
                }
                StructuralWriteCoordinator.Outcome.Panic -> {
                    Log.e(TAG, "PANIC pid=$pid opId=${params.opId}")
                    AutoCalculationResult.Failure("Расчёт прерван по тайм-ауту")
                }
                is StructuralWriteCoordinator.Outcome.Error -> {
                    Log.e(TAG, "ERROR pid=$pid opId=${params.opId}", out.throwable)
                    AutoCalculationResult.Failure(
                        out.throwable.message ?: "Не удалось сохранить рассчитанные линии"
                    )
                }
            }
        } finally {
            // Возвращаем activeProjectId назад (если переключали), чтобы не ломать UI-контекст.
            if (needSwitch) {
                runCatching {
                    activeProjectDs.setActiveProjectId(previousActivePid)
                }.onFailure {
                    Log.e(TAG, "ACTIVE_PID_RESTORE_FAILED previous=$previousActivePid opId=${params.opId}", it)
                }.onSuccess {
                    Log.w(TAG, "ACTIVE_PID_RESTORE restored=$previousActivePid opId=${params.opId}")
                }
            }
        }
    }
}
