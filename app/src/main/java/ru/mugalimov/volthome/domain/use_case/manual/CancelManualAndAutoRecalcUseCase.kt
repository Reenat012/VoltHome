package ru.mugalimov.volthome.domain.use_case.manual

import android.util.Log
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory

/**
 * Cancel manual -> полный авто-пересчёт -> сохранение результата в локальную БД.
 *
 * ВАЖНО:
 * - manual-сессию удаляет вызывающая сторона (MainApp через manualRepo.exitManualMode),
 *   этот usecase делает только авто-пересчёт и commit результата в БД.
 * - projectId передаём ЯВНО, чтобы не было гонок при смене активного проекта.
 */
@Singleton
class CancelManualAndAutoRecalcUseCase @Inject constructor(
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: PreferencesRepository,
    private val explicationRepository: ExplicationRepository,
) {

    data class Params(
        val projectId: String
    )

    /**
     * Выполняет полный авто-пересчёт по текущему PhaseMode и сохраняет результат в БД (строго в projectId).
     */
    suspend fun execute(params: Params): GroupingResult {
        Log.e(
            "MANUAL_CANCEL",
            "CANCEL_USE_CASE EXECUTE pid=${params.projectId}",
            Throwable("STACK")
        )
        val projectId = params.projectId
        if (projectId.isBlank()) {
            return GroupingResult.Error("projectId пуст")
        }

        // 1) Берём актуальный режим фаз из preferences (источник истины для пересчёта).
        val mode = preferencesRepository.phaseMode.first()

        // 2) Считаем
        val calc = groupCalculatorFactory.create()
        return when (val res = calc.calculateGroups(mode)) {
            is GroupingResult.Error -> res
            is GroupingResult.Success -> {
                // 3) Сохраняем строго в projectId
                val groups = res.system.groups
                explicationRepository.replaceAllGroupsTransactional(projectId = projectId, groups = groups)

                // 4) Decision log — in-memory, можно писать как раньше
                explicationRepository.setLastDistributionDecisions(res.distributionDecisions)
                res
            }
        }
    }
}