package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.mapper.mapToDomainGroupsFromRelations
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.phase_load.PhaseDeviceItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

/**
 * Источник PhaseLoadItem для AUTO режима.
 *
 * ВАЖНО (Коммит 5):
 * - MANUAL больше НЕ строится здесь (он строится во ViewModel из draft).
 * - Здесь оставляем только AUTO-пайплайн: БД + overrides.
 *
 * Коммит 2:
 * - UI path больше не держит собственную формулу;
 * - он использует тот же canonical calculation core, что и остальные пути.
 */
class GetPhaseLoadUiUseCase @Inject constructor(
    private val groupDao: GroupDao,
    private val overrideDao: GroupPhaseOverrideDao,
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
                    Log.w(
                        "PHASE_OVR",
                        "AUTO overrides projectId=$projectId count=${overrides.size} sample=" +
                                overrides.take(10).joinToString { "${it.groupId}->${it.phase}" }
                    )

                    val groupsFromDb: List<CircuitGroup> = relations.mapToDomainGroupsFromRelations()

                    CalculationTrace.log(
                        stage = "PHASE_LOAD_AUTO_SOURCE",
                        message =
                            "projectId=$projectId groupsFromDb=${groupsFromDb.size} overrides=${overrides.size}"
                    )

                    val overrideMap = overrides.associate { it.groupId to it.phase }

                    groupsFromDb.map { g ->
                        val forced = overrideMap[g.groupId]
                        if (forced != null && forced != g.phase) g.copy(phase = forced) else g
                    }
                }.map { groupsWithOverrides ->
                    buildPhaseItems(groupsWithOverrides)
                }
            }
            .flowOn(dispatcher)
    }

    private fun buildPhaseItems(groups: List<CircuitGroup>): List<PhaseLoadItem> {
        CalculationTrace.log(
            stage = "PHASE_LOAD_AUTO_BUILD_START",
            message =
                "groups=${groups.size} mode=AUTO path=GetPhaseLoadUiUseCase.buildPhaseItems() note=UI path recalculates totals from devices for characterization"
        )

        // 1) Сбор 3φ пула: устройства с AC_3PHASE
        val threePhaseDevices = groups
            .flatMap { g -> g.devices.map { d -> g to d } }
            .filter { (_, d) -> d.voltage.type == VoltageType.AC_3PHASE }

        val threePhaseGroups: List<PhaseGroupItem> =
            threePhaseDevices
                .groupBy(
                    keySelector = { (g, _) -> g.groupId },
                    valueTransform = { (g, d) -> g to d }
                )
                .values
                .map { items ->
                    val g = items.first().first
                    val devices = items.map { it.second }

                    val deviceRows = devices.map { d ->
                        PhaseDeviceItem(
                            deviceId = d.id,
                            name = d.name,
                            power = d.power.toDouble(),
                            current = d.calculateCurrent()
                        )
                    }

                    val groupLoad = CurrentCalculator.calculateGroupLoad(
                        devices.map { d ->
                            LoadInput(
                                powerW = d.power.toDouble(),
                                voltage = d.voltage.value.toDouble(),
                                powerFactor = d.powerFactor,
                                demandRatio = d.demandRatio,
                                voltageType = d.voltage.type,
                                label = d.name
                            )
                        }
                    )

                    CalculationTrace.log(
                        stage = "PHASE_LOAD_AUTO_GROUP_UI_TOTAL",
                        message =
                            "groupNumber=${g.groupNumber} groupId=${g.groupId} phase=${g.phase} " +
                                    "uiTotalCurrentA=${CalculationTrace.f(groupLoad.calculatedCurrentA)} " +
                                    "groupNominalCurrentField=${CalculationTrace.f(g.nominalCurrent)} " +
                                    "segment=THREE_PHASE"
                    )

                    PhaseGroupItem(
                        groupId = g.groupId,
                        groupNumber = g.groupNumber,
                        roomName = g.roomName,
                        devices = deviceRows,
                        roomId = g.roomId,
                        totalPower = deviceRows.sumOf { it.power },
                        totalCurrent = groupLoad.calculatedCurrentA
                    )
                }

        val p3Total = threePhaseGroups.sumOf { it.totalPower }
        val i3Total = threePhaseGroups.sumOf { it.totalCurrent }

        val p3PerPhase = p3Total / 3.0
        val i3PerPhase = i3Total / 3.0

        // 2) Секции A/B/C
        val phaseItems = listOf(Phase.A, Phase.B, Phase.C).map { phase ->
            val groupsOfPhase = groups.filter { it.phase == phase }

            val groupRows: List<PhaseGroupItem> = groupsOfPhase.map { g ->
                val onePhaseDevices = g.devices.filter { it.voltage.type != VoltageType.AC_3PHASE }

                val deviceRows = onePhaseDevices.map { d ->
                    PhaseDeviceItem(
                        deviceId = d.id,
                        name = d.name,
                        power = d.power.toDouble(),
                        current = d.calculateCurrent()
                    )
                }

                val groupLoad = CurrentCalculator.calculateGroupLoad(
                    onePhaseDevices.map { d ->
                        LoadInput(
                            powerW = d.power.toDouble(),
                            voltage = d.voltage.value.toDouble(),
                            powerFactor = d.powerFactor,
                            demandRatio = d.demandRatio,
                            voltageType = d.voltage.type,
                            label = d.name
                        )
                    }
                )

                CalculationTrace.log(
                    stage = "PHASE_LOAD_AUTO_GROUP_UI_TOTAL",
                    message =
                        "groupNumber=${g.groupNumber} groupId=${g.groupId} phase=${g.phase} " +
                                "uiTotalCurrentA=${CalculationTrace.f(groupLoad.calculatedCurrentA)} " +
                                "groupNominalCurrentField=${CalculationTrace.f(g.nominalCurrent)} " +
                                "segment=ONE_PHASE"
                )

                PhaseGroupItem(
                    groupId = g.groupId,
                    groupNumber = g.groupNumber,
                    roomName = g.roomName,
                    devices = deviceRows,
                    roomId = g.roomId,
                    totalPower = deviceRows.sumOf { it.power },
                    totalCurrent = groupLoad.calculatedCurrentA
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

        val threePhaseItem = PhaseLoadItem(
            phase = Phase.THREE_PHASE,
            groups = threePhaseGroups,
            totalPower = p3Total,
            totalCurrent = i3Total
        )

        val result = phaseItems + threePhaseItem

        CalculationTrace.log(
            stage = "PHASE_LOAD_AUTO_BUILD_FINISH",
            message =
                "items=${result.size} totals=" +
                        result.joinToString { "${it.phase}:${CalculationTrace.f(it.totalCurrent)}A" }
        )

        return result
    }
}