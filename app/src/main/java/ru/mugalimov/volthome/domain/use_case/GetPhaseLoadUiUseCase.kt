package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import javax.inject.Inject

class GetPhaseLoadUiUseCase @Inject constructor(
    private val repository: ExplicationRepository
) {
    suspend operator fun invoke(): List<PhaseLoadItem> {
        val allGroups = repository.getGroupsWithDevices()

        // Группируем по фазам
        val groupedByPhase = allGroups.groupBy { it.group.phase }

        return Phase.values().map { phase ->
            val groups = groupedByPhase[phase].orEmpty()

            val groupItems = groups.map { groupWithDevices ->
                val group = groupWithDevices.group
                val devices = groupWithDevices.devices

                PhaseGroupItem(
                    groupNumber = group.groupNumber,
                    roomName = group.roomName,
                    devices = devices.map { it.name },
                    totalPower = devices.sumOf { it.power.toDouble() },
                    totalCurrent = devices.sumOf { device ->
                        val power = device.power.toDouble()
                        val voltage = device.voltage.value.takeIf { it > 0 } ?: 230.0
                        val powerFactor = (device.powerFactor ?: 1.0).coerceIn(0.8, 1.0)
                        when (device.voltage.type) {
                            VoltageType.AC_1PHASE -> power / (voltage.toDouble() * powerFactor)
                            VoltageType.AC_3PHASE -> power / (1.732 * voltage.toDouble() * powerFactor)
                            VoltageType.DC -> power / voltage.toDouble()
                        }
                    }
                )
            }

            PhaseLoadItem(
                phase = phase,
                totalPower = groupItems.sumOf { it.totalPower },
                totalCurrent = groupItems.sumOf { it.totalCurrent },
                groups = groupItems
            )
        }
    }
}
