package ru.mugalimov.volthome.domain.use_case.phase_load

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.phase_load.PhaseDeviceItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator

/**
 * Единый детерминированный builder PhaseLoadItem из доменных групп.
 *
 * ВАЖНО:
 * - в MANUAL сюда приходят группы из draft + devicesById
 * - в AUTO сюда приходят группы из DB (с applied overrides)
 */
object PhaseLoadItemsBuilder {

    fun build(groups: List<CircuitGroup>): List<PhaseLoadItem> {
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

                    PhaseGroupItem(
                        groupId = g.groupId,
                        groupNumber = g.groupNumber,
                        roomName = g.roomName,
                        devices = deviceRows,
                        roomId = g.roomId,
                        totalPower = deviceRows.sumOf { it.power },
                        totalCurrent = items.sumOf { (_, d) ->
                            CurrentCalculator.calculateNominalCurrent(
                                power = d.power.toDouble(),
                                voltage = (d.voltage.value.takeIf { it > 0 } ?: 230).toDouble(),
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

                PhaseGroupItem(
                    groupId = g.groupId,
                    groupNumber = g.groupNumber,
                    roomName = g.roomName,
                    devices = deviceRows,
                    roomId = g.roomId,
                    totalPower = deviceRows.sumOf { it.power },
                    totalCurrent = onePhaseDevices.sumOf { d ->
                        CurrentCalculator.calculateNominalCurrent(
                            power = d.power.toDouble(),
                            voltage = (d.voltage.value.takeIf { it > 0 } ?: 230).toDouble(),
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

        // 3) отдельный блок 3φ
        val threePhaseItem = PhaseLoadItem(
            phase = Phase.THREE_PHASE,
            groups = threePhaseGroups,
            totalPower = p3Total,
            totalCurrent = i3Total
        )

        return phaseItems + threePhaseItem
    }
}