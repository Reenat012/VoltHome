package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import javax.inject.Inject

class GetPhaseLoadUiUseCase @Inject constructor(
    private val repository: ExplicationRepository
) {
    suspend operator fun invoke(): List<PhaseLoadItem> {
        val groups = repository.getGroupsWithDevices()
        val phases = Phase.values()

        return phases.map { phase ->
            val phaseGroups = groups.filter { it.group.phase == phase }

            val groupItems = phaseGroups.map { group ->
                val devices = group.devices
                PhaseGroupItem(
                    groupNumber = group.group.groupNumber,
                    roomName = group.group.roomName,
                    devices = devices.map { it.name },
                    totalPower = devices.sumOf { it.power.toDouble() },
                    totalCurrent = group.group.nominalCurrent // единый источник истины
                )
            }

            val totalCurrent = groupItems.sumOf { it.totalCurrent }
            val totalPower = groupItems.sumOf { it.totalPower }

            PhaseLoadItem(
                phase = phase,
                totalCurrent = totalCurrent,
                totalPower = totalPower,
                groups = groupItems
            )
        }
    }
}
