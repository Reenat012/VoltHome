package ru.mugalimov.volthome.domain.use_case.phase_load

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.phase_load.PhaseDeviceItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import ru.mugalimov.volthome.domain.use_case.CalculationTrace
import ru.mugalimov.volthome.domain.use_case.CircuitLoadCalculator
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator
import ru.mugalimov.volthome.domain.use_case.LoadInput

/**
 * Единый детерминированный builder PhaseLoadItem из доменных групп.
 *
 * ВАЖНО:
 * - в MANUAL сюда приходят группы из draft + devicesById
 * - в AUTO сюда приходят группы из DB (с applied overrides)
 *
 * Коммит 2:
 * - manual/ui path использует то же calculation core, что и AUTO/breakdown.
 */
object PhaseLoadItemsBuilder {

    fun build(groups: List<CircuitGroup>): List<PhaseLoadItem> {
        CalculationTrace.log(
            stage = "PHASE_LOAD_BUILDER_START",
            message =
                "groups=${groups.size} path=PhaseLoadItemsBuilder.build() note=UI/manual aggregation path uses canonical calculation core"
        )

        val threePhaseDevices = groups
            .flatMap { g -> g.devices.map { d -> g to d } }
            .filter { (_, d) -> d.voltage.type == VoltageType.AC_3PHASE }

        groups.forEach { group ->
            val voltageTypes = group.devices.map { it.voltage.type }.distinct()
            require(voltageTypes.size <= 1) {
                "Группа №${group.groupNumber} смешивает 1ф и 3ф устройства"
            }
            val containsThreePhase = voltageTypes.singleOrNull() == VoltageType.AC_3PHASE
            require(!containsThreePhase || group.phase == Phase.THREE_PHASE) {
                "Группа №${group.groupNumber} с 3ф устройством имеет некорректную фазу ${group.phase}"
            }
        }

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
                        val load = CurrentCalculator.calculateDeviceLoad(
                            LoadInput(
                                powerW = d.power.toDouble(),
                                voltage = d.voltage.value.toDouble(),
                                powerFactor = d.powerFactor,
                                demandRatio = d.demandRatio,
                                voltageType = d.voltage.type,
                                label = d.name
                            )
                        )
                        PhaseDeviceItem(
                            deviceId = d.id,
                            name = d.name,
                            power = d.power.toDouble(),
                            current = load.calculatedCurrentA,
                            installedCurrent = load.installedCurrentA
                        )
                    }
                    val groupLoad = CircuitLoadCalculator.calculate(devices)

                    CalculationTrace.log(
                        stage = "PHASE_LOAD_BUILDER_GROUP_UI_TOTAL",
                        message =
                            "groupNumber=${g.groupNumber} groupId=${g.groupId} phase=${g.phase} " +
                                    "uiTotalCurrentA=${CalculationTrace.f(g.nominalCurrent)} " +
                                    "groupNominalCurrentField=${CalculationTrace.f(g.nominalCurrent)} " +
                                    "segment=THREE_PHASE"
                    )

                    PhaseGroupItem(
                        groupId = g.groupId,
                        groupNumber = g.groupNumber,
                        roomName = g.roomName,
                        devices = deviceRows,
                        roomId = g.roomId,
                        totalPower = groupLoad.installedPowerW,
                        totalCurrent = g.nominalCurrent,
                        installedCurrent = groupLoad.installedCurrentA
                    )
                }

        val p3Total = threePhaseGroups.sumOf { it.totalPower }
        val i3Total = threePhaseGroups.sumOf { it.totalCurrent }

        val p3PerPhase = p3Total / 3.0
        // calculatedCurrentA для 3ф уже является линейным током каждой фазы.
        val i3PerPhase = i3Total

        val phaseItems = listOf(Phase.A, Phase.B, Phase.C).map { phase ->
            val groupsOfPhase = groups.filter { it.phase == phase }

            val groupRows: List<PhaseGroupItem> = groupsOfPhase.map { g ->
                val onePhaseDevices = g.devices.filter { it.voltage.type != VoltageType.AC_3PHASE }

                val deviceRows = onePhaseDevices.map { d ->
                    val load = CurrentCalculator.calculateDeviceLoad(
                        LoadInput(
                            powerW = d.power.toDouble(),
                            voltage = d.voltage.value.toDouble(),
                            powerFactor = d.powerFactor,
                            demandRatio = d.demandRatio,
                            voltageType = d.voltage.type,
                            label = d.name
                        )
                    )
                    PhaseDeviceItem(
                        deviceId = d.id,
                        name = d.name,
                        power = d.power.toDouble(),
                        current = load.calculatedCurrentA,
                        installedCurrent = load.installedCurrentA
                    )
                }
                val groupLoad = CircuitLoadCalculator.calculate(onePhaseDevices)

                CalculationTrace.log(
                    stage = "PHASE_LOAD_BUILDER_GROUP_UI_TOTAL",
                    message =
                        "groupNumber=${g.groupNumber} groupId=${g.groupId} phase=${g.phase} " +
                                "uiTotalCurrentA=${CalculationTrace.f(g.nominalCurrent)} " +
                                "groupNominalCurrentField=${CalculationTrace.f(g.nominalCurrent)} " +
                                "segment=ONE_PHASE"
                )

                PhaseGroupItem(
                    groupId = g.groupId,
                    groupNumber = g.groupNumber,
                    roomName = g.roomName,
                    devices = deviceRows,
                    roomId = g.roomId,
                    totalPower = groupLoad.installedPowerW,
                    totalCurrent = g.nominalCurrent,
                    installedCurrent = groupLoad.installedCurrentA
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
            stage = "PHASE_LOAD_BUILDER_FINISH",
            message =
                "items=${result.size} totals=" +
                        result.joinToString { "${it.phase}:${CalculationTrace.f(it.totalCurrent)}A" }
        )

        return result
    }
}
