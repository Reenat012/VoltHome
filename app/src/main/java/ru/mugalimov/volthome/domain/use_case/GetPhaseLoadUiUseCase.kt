// ru/mugalimov/volthome/domain/use_case/GetPhaseLoadUiUseCase.kt
package ru.mugalimov.volthome.domain.use_case

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.mapper.mapToDomainGroupsFromRelations
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseDeviceItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

class GetPhaseLoadUiUseCase @Inject constructor(
    private val groupDao: GroupDao,
    private val overrideDao: GroupPhaseOverrideDao, // ✅ добавили
    private val activeDs: ActiveProjectDataStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    operator fun invoke(): Flow<List<PhaseLoadItem>> {
        return activeDs.activeProjectId
            .distinctUntilChanged()
            .filterNotNull()
            .flatMapLatest { projectId ->
                combine(
                    groupDao.observeGroupsWithDevicesByProject(projectId),
                    overrideDao.observeByProject(projectId)
                ) { relations, overrides ->
                    val groups = relations.mapToDomainGroupsFromRelations() // -> List<CircuitGroup>

                    // groupId -> Phase (override)
                    val overrideMap = overrides.associate { it.groupId to it.phase }

                    // ✅ применяем override ДО сборки PhaseLoadItem
                    groups.map { g ->
                        val forced = overrideMap[g.groupId]
                        if (forced != null && forced != g.phase) g.copy(phase = forced) else g
                    }
                }
            }
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
                    groupId = g.groupId,                 // ✅ добавили
                    groupNumber = g.groupNumber,
                    roomName = g.roomName,
                    devices = deviceRows,
                    roomId = g.roomId,
                    totalPower = g.devices.sumOf { it.power.toDouble() },
                    totalCurrent = g.devices.sumOf { d ->
                        CurrentCalculator.calculateNominalCurrent(
                            power       = d.power.toDouble(),
                            voltage     = (d.voltage.value.takeIf { it > 0 } ?: 230).toDouble(),
                            powerFactor = d.powerFactor,
                            demandRatio = d.demandRatio,
                            voltageType = d.voltage.type
                        )
                    }
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