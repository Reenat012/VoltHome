package ru.mugalimov.volthome.domain.use_case

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.model.ProjectCoverage
import javax.inject.Inject

/**
 * Сравнивает полный каталог устройств проекта с фактическим составом групп.
 * Работает и для сохранённой структуры, и для активного ручного черновика.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveProjectCoverageUseCase @Inject constructor(
    private val activeProjectDataStore: ActiveProjectDataStore,
    private val explicationRepository: ExplicationRepository,
    private val manualRepository: ManualEditSessionRepository
) {
    operator fun invoke(): Flow<ProjectCoverage> =
        activeProjectDataStore.activeProjectId
            .distinctUntilChanged()
            .flatMapLatest { projectId ->
                val pid = projectId.orEmpty().trim()
                if (pid.isBlank()) {
                    flowOf(ProjectCoverage.Empty)
                } else {
                    combine(
                        explicationRepository.observeAllDevicesByProject(pid),
                        explicationRepository.observeAllGroupByProject(pid),
                        manualRepository.observeSession(pid)
                    ) { devices, groups, session ->
                        val assignedIds = if (session?.manualModeActive == true) {
                            session.draftState.groups
                                .asSequence()
                                .flatMap { it.deviceIds.asSequence() }
                                .toSet()
                        } else {
                            groups.asSequence()
                                .flatMap { it.devices.asSequence() }
                                .map { it.id }
                                .toSet()
                        }
                        ProjectCoverageCalculator.calculate(devices, assignedIds)
                    }
                }
            }
            .distinctUntilChanged()
}
