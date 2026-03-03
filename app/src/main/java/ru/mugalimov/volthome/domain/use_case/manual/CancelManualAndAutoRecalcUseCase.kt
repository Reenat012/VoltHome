package ru.mugalimov.volthome.domain.use_case.manual

import android.util.Log
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.ProjectStructuralWriteMutex
import ru.mugalimov.volthome.domain.use_case.SaveAutoCalculatedGroupsToLocalDbUseCase

@Singleton
@Deprecated(
    message = "Запрещено по ТЗ: Cancel НЕ должен запускать auto-recalc и писать группы. " +
            "Используйте manualRepo.exitManualMode(projectId) и возвращайтесь к DB pipeline.",
    level = DeprecationLevel.ERROR
)
class CancelManualAndAutoRecalcUseCase @Inject constructor(
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: PreferencesRepository,
    private val saveAutoCalculatedGroupsToLocalDbUseCase: SaveAutoCalculatedGroupsToLocalDbUseCase,
    private val structuralWriteMutex: ProjectStructuralWriteMutex,
) {

    data class Params(
        val projectId: String,
        // ✅ Commit 4: корреляция операции (может быть null, если вызывающий контур не даёт opId)
        val opId: String? = null
    )

    suspend fun execute(params: Params): GroupingResult {
        val projectId = params.projectId.trim()
        val opId = params.opId

        Log.w("MANUAL_CANCEL", "CANCEL_USE_CASE EXECUTE pid=$projectId opId=$opId")

        if (projectId.isBlank()) {
            return GroupingResult.Error("projectId пуст")
        }

        /**
         * ✅ ВАЖНО:
         * Мы уже под structural lock, поэтому внутри НЕЛЬЗЯ звать методы,
         * которые снова пытаются взять тот же Mutex (он не реентерабельный).
         */
        return structuralWriteMutex.withLock(projectId) {
            val mode = preferencesRepository.phaseMode.first()
            val calc = groupCalculatorFactory.create(projectId)

            when (val res = calc.calculateGroups(mode)) {
                is GroupingResult.Error -> res
                is GroupingResult.Success -> {
                    // ✅ Вызываем вариант "я уже под lock"
                    // ✅ Commit 4: source/opId прокидываем в AUTO_SAVE
                    saveAutoCalculatedGroupsToLocalDbUseCase.executeAlreadyLocked(
                        SaveAutoCalculatedGroupsToLocalDbUseCase.Params(
                            projectId = projectId,
                            groups = res.system.groups,
                            distributionDecisions = res.distributionDecisions,
                            source = "CancelManualAndAutoRecalcUseCase",
                            opId = opId
                        )
                    )
                    res
                }
            }
        }
    }
}