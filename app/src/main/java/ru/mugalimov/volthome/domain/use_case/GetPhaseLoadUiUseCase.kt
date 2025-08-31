package ru.mugalimov.volthome.domain.use_case

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.mapper.mapToDomainGroupsFromRelations
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseDeviceItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import javax.inject.Inject

class GetPhaseLoadUiUseCase @Inject constructor(
    private val repo: ExplicationRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    operator fun invoke(): Flow<List<PhaseLoadItem>> {
        return repo.observeGroupsWithDevices() // Flow<List<CircuitGroupWithDevices>>
            .map { relations -> relations.mapToDomainGroupsFromRelations() } // → List<CircuitGroup>
            .map { groups -> buildPhaseItems(groups) }
            .flowOn(dispatcher)
    }

    private fun buildPhaseItems(groups: List<CircuitGroup>): List<PhaseLoadItem> {
        return listOf(Phase.A, Phase.B, Phase.C).map { phase ->
            val groupsOfPhase = groups.filter { it.phase == phase }

            val groupRows: List<PhaseGroupItem> = groupsOfPhase.map { g ->
                val deviceRows = g.devices.map { d ->
                    PhaseDeviceItem(
                        name = d.name,
                        power = d.power.toDouble(),
                        current = d.calculateCurrent()
                    )
                }

                PhaseGroupItem(
                    groupNumber = g.groupNumber,
                    roomName = g.roomName,
                    devices = deviceRows,
                    totalPower = g.devices.sumOf { it.power.toDouble() },
                    totalCurrent = g.devices.sumOf { it.calculateCurrent() }
                )
            }

            PhaseLoadItem(
                phase = phase,
                groups = groupRows,
                totalPower = groupRows.sumOf { it.totalPower },
                totalCurrent = groupRows.sumOf { it.totalCurrent }
            )
        }
    }
}