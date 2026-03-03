package ru.mugalimov.volthome.domain.use_case.manual

import android.util.Log
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.repository.ManualOverridesCleanerRepository
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.ProjectStructuralWriteMutex
import ru.mugalimov.volthome.domain.use_case.SaveAutoCalculatedGroupsToLocalDbUseCase

class ResetManualOverridesAndAutoRecalcUseCase @Inject constructor(
    private val manualOverridesCleanerRepository: ManualOverridesCleanerRepository,
    private val projectOwnershipRepository: ProjectOwnershipRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val saveAutoCalculatedGroupsToLocalDbUseCase: SaveAutoCalculatedGroupsToLocalDbUseCase,
    private val structuralWriteMutex: ProjectStructuralWriteMutex,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    data class Params(
        val projectId: String,
        val phaseMode: PhaseMode
    )

    suspend fun execute(params: Params): GroupingResult =
        withContext(ioDispatcher) {
            val pid = params.projectId.trim()
            if (pid.isBlank()) return@withContext GroupingResult.Error("projectId пустой")

            // ✅ opId корреляции для всей операции reset -> recalc -> save
            val opId = "manual-reset-" + UUID.randomUUID().toString()

            structuralWriteMutex.withLock(pid) {

                Log.w("MANUAL_RESET", "USECASE START pid=$pid mode=${params.phaseMode} opId=$opId")

                // 1) Чистим ТОЛЬКО overrides. joins НЕ трогаем.
                val clean = manualOverridesCleanerRepository.clearAllManualOverrides(pid)
                if (!clean) {
                    Log.e("MANUAL_RESET", "RESET overrides FAILED pid=$pid opId=$opId")
                    return@withLock GroupingResult.Error("Не удалось удалить ручные overrides (DB)")
                }

                // 2) Снимаем ownership lock ДО auto-save, иначе save будет suppressed.
                projectOwnershipRepository.setManualLock(pid, false)
                Log.w("MANUAL_RESET", "manualLock=false pid=$pid opId=$opId")

                // 3) Полный auto-recalc (в правильном projectId контексте)
                val calc = groupCalculatorFactory.create(pid)

                when (val res = calc.calculateGroups(params.phaseMode)) {
                    is GroupingResult.Error -> {
                        Log.e("MANUAL_RESET", "AUTO_RECALC ERROR pid=$pid opId=$opId msg=${res.message}")

                        // Тут твой комментарий был правильный:
                        // Reset = пользователь хотел выйти из manual.
                        // Возвращать lock=true после ошибки — спорно. Сейчас НЕ возвращаем.
                        res
                    }

                    is GroupingResult.Success -> {
                        val groups = res.system.groups
                        Log.w("MANUAL_RESET", "AUTO_RECALC OK pid=$pid opId=$opId groups=${groups.size} -> SAVE")

                        // 4) Сохраняем авто-структуру (уже не suppressed, т.к. manualLock=false)
                        // ⚠️ ВАЖНО: executeAlreadyLocked допустим, потому что мы уже внутри structuralWriteMutex.withLock(pid).
                        saveAutoCalculatedGroupsToLocalDbUseCase.executeAlreadyLocked(
                            SaveAutoCalculatedGroupsToLocalDbUseCase.Params(
                                projectId = pid,
                                groups = groups,
                                distributionDecisions = res.distributionDecisions,
                                source = "ResetManualOverridesAndAutoRecalcUseCase",
                                opId = opId
                            )
                        )

                        Log.w("MANUAL_RESET", "USECASE END OK pid=$pid opId=$opId groups=${groups.size}")
                        res
                    }
                }
            }
        }
}