package ru.mugalimov.volthome.domain.use_case.phase_load

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.phase_load.PhaseDeviceItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import ru.mugalimov.volthome.domain.use_case.CalculationTrace
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator

/**
 * Единый детерминированный builder PhaseLoadItem из доменных групп.
 *
 * ВАЖНО:
 * - в MANUAL сюда приходят группы из draft + devicesById
 * - в AUTO сюда приходят группы из DB (с applied overrides)
 *
 * ВАЖНО (Коммит 1):
 * - это НЕ canonical source of truth для группового тока;
 * - это UI aggregation path, который повторно суммирует device currents;
 * - этот путь сохраняем как characterization path до следующего коммита.
 */
object PhaseLoadItemsBuilder {

    fun build(groups: List<CircuitGroup>): List<PhaseLoadItem> {
        CalculationTrace.log(
            stage = "PHASE_LOAD_BUILDER_START",
            message =
                "groups=${groups.size} path=PhaseLoadItemsBuilder.build() " +
                        "note=UI/manual aggregation path recalculates totals from devices"
        )

        // 1) 3φ пул: устройства с AC_3PHASE, независимо от фазы группы
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
                    val deviceRows = items.map { (_, d) ->
                        PhaseDeviceItem(
                            deviceId = d.id,
                            name = d.name,
                            power = d.power.toDouble(),
                            current = d.calculateCurrent()
                        )
                    }

                    val uiCurrent = items.sumOf { (_, d) ->
                        CurrentCalculator.calculateNominalCurrent(
                            power = d.power.toDouble(),
                            voltage = (d.voltage.value.takeIf { it > 0 } ?: 230).toDouble(),
                            powerFactor = d.powerFactor,
                            demandRatio = d.demandRatio,
                            voltageType = d.voltage.type
                        )
                    }

                    CalculationTrace.log(
                        stage = "PHASE_LOAD_BUILDER_GROUP_UI_TOTAL",
                        message =
                            "groupNumber=${g.groupNumber} groupId=${g.groupId} phase=${g.phase} " +
                                    "uiTotalCurrentA=${CalculationTrace.f(uiCurrent)} " +
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
                        totalCurrent = uiCurrent
                    )
                }

        val p3Total = threePhaseGroups.sumOf { it.totalPower }
        val i3Total = threePhaseGroups.sumOf { it.totalCurrent }

        val p3PerPhase = p3Total / 3.0
        val i3PerPhase = i3Total / 3.0

        // 2) A/B/C: внутри групп только НЕ 3φ устройства
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

                val uiCurrent = onePhaseDevices.sumOf { d ->
                    CurrentCalculator.calculateNominalCurrent(
                        power = d.power.toDouble(),
                        voltage = (d.voltage.value.takeIf { it > 0 } ?: 230).toDouble(),
                        powerFactor = d.powerFactor,
                        demandRatio = d.demandRatio,
                        voltageType = d.voltage.type
                    )
                }

                CalculationTrace.log(
                    stage = "PHASE_LOAD_BUILDER_GROUP_UI_TOTAL",
                    message =
                        "groupNumber=${g.groupNumber} groupId=${g.groupId} phase=${g.phase} " +
                                "uiTotalCurrentA=${CalculationTrace.f(uiCurrent)} " +
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
                    totalCurrent = uiCurrent
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

        // 3) отдельный блок 3φ
        val threePhaseItem = PhaseLoadItem(
            phase = Phase.THREE_PHASE,
            groups = threePhaseGroups,
            totalPower = p3Total,
            totalCurrent = i3Total
        )

        val result = phaseItems + threePhaseItem

        CalculationTrace.log(
            stage = "PHASE_LOAD_BUILDER_FINISH",
            message =
                "items=${result.size} totals=" +
                        result.joinToString { "${it.phase}:${CalculationTrace.f(it.totalCurrent)}A" }
        )

        return result
    }
}