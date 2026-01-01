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
        // 1) Сбор 3φ пула: устройства с AC_3PHASE (вне зависимости от фазы группы)
        val threePhaseDevices = groups
            .flatMap { g -> g.devices.map { d -> g to d } }
            .filter { (_, d) -> d.voltage.type == ru.mugalimov.volthome.domain.model.VoltageType.AC_3PHASE }

        val threePhaseGroups: List<PhaseGroupItem> =
            threePhaseDevices
                .groupBy(
                    keySelector = { (g, _) -> g.groupId },
                    valueTransform = { (g, d) -> g to d }
                )
                .values
                .map { items ->
                    val g = items.first().first
                    val deviceRows = items.map { (_, d) ->
                        PhaseDeviceItem(
                            name = d.name,
                            power = d.power.toDouble(),
                            current = d.calculateCurrent()
                        )
                    }

                    PhaseGroupItem(
                        groupId = g.groupId,
                        groupNumber = g.groupNumber,
                        roomName = g.roomName,
                        devices = deviceRows,
                        roomId = g.roomId,
                        totalPower = deviceRows.sumOf { it.power },
                        totalCurrent = items.sumOf { (_, d) ->
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

        val p3Total = threePhaseGroups.sumOf { it.totalPower }
        val i3Total = threePhaseGroups.sumOf { it.totalCurrent }

        val p3PerPhase = p3Total / 3.0
        val i3PerPhase = i3Total / 3.0

        // 2) Секции A/B/C: берём только группы A/B/C и внутри них — только НЕ 3φ устройства
        val phaseItems = listOf(Phase.A, Phase.B, Phase.C).map { phase ->
            val groupsOfPhase = groups.filter { it.phase == phase }

            val groupRows: List<PhaseGroupItem> = groupsOfPhase.map { g ->
                val onePhaseDevices = g.devices.filter { it.voltage.type != ru.mugalimov.volthome.domain.model.VoltageType.AC_3PHASE }

                val deviceRows = onePhaseDevices.map { d ->
                    PhaseDeviceItem(
                        name = d.name,
                        power = d.power.toDouble(),
                        current = d.calculateCurrent()
                    )
                }

                PhaseGroupItem(
                    groupId = g.groupId,
                    groupNumber = g.groupNumber,
                    roomName = g.roomName,
                    devices = deviceRows,
                    roomId = g.roomId,
                    totalPower = deviceRows.sumOf { it.power },
                    totalCurrent = onePhaseDevices.sumOf { d ->
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

            val p1 = groupRows.sumOf { it.totalPower }
            val i1 = groupRows.sumOf { it.totalCurrent }

            PhaseLoadItem(
                phase = phase,
                groups = groupRows,
                totalPower = p1 + p3PerPhase,
                totalCurrent = i1 + i3PerPhase
            )
        }

        // 3) Отдельный блок 3φ как отдельный PhaseLoadItem, чтобы не ломать контракт List<PhaseLoadItem>
        val threePhaseItem = PhaseLoadItem(
            phase = Phase.THREE_PHASE,
            groups = threePhaseGroups,
            totalPower = p3Total,
            totalCurrent = i3Total
        )

        return phaseItems + threePhaseItem
    }
}