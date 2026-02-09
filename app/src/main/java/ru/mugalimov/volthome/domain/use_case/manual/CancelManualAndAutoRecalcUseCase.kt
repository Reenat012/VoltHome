package ru.mugalimov.volthome.domain.use_case.manual

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory

/**
 * Cancel manual -> полный авто-пересчёт -> сохранение результата в локальную БД.
 *
 * ВАЖНО:
 * - manual сессию удаляет ViewModel (через manualRepo.exitManualMode),
 *   этот usecase делает только авто-пересчёт и commit результата в БД.
 */
class CancelManualAndAutoRecalcUseCase @Inject constructor(
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: PreferencesRepository,
    private val explicationRepository: ExplicationRepository,
) {

    data class Result(
        val mode: ru.mugalimov.volthome.domain.model.PhaseMode,
        val groups: List<ru.mugalimov.volthome.domain.model.CircuitGroup>,
        val decisions: List<ru.mugalimov.volthome.domain.model.DistributionDecision>
    )

    suspend fun execute(): GroupingResult {
        val mode = preferencesRepository.phaseMode.first()

        val calc = groupCalculatorFactory.create()
        return when (val res = calc.calculateGroups(mode)) {
            is GroupingResult.Error -> res
            is GroupingResult.Success -> {
                val groups = res.system.groups
                explicationRepository.replaceAllGroupsTransactional(groups)
                explicationRepository.setLastDistributionDecisions(res.distributionDecisions)
                res
            }
        }
    }
}