package ru.mugalimov.volthome.domain.use_case

import android.util.Log
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
        val groups = repository.getGroupsWithDevices()
        val phases = Phase.values()

        groups.forEach {
            Log.d("PHASE_CHECK", "Группа №${it.group.groupNumber} — Фаза: ${it.group.phase}")
        }

        return phases.map { phase ->
            val phaseGroups = groups.filter { it.group.phase == phase }

            val groupItems = phaseGroups.map { group ->
                val devices = group.devices

                PhaseGroupItem(
                    groupNumber = group.group.groupNumber,
                    roomName = group.group.roomName,
                    devices = devices.map { it.name },
                    totalPower = devices.sumOf { it.power.toDouble() },
                    totalCurrent = devices.sumOf { device ->
                        val power = device.power.toDouble()
                        val voltage = device.voltage.value.takeIf { it > 0 }?.toDouble() ?: 230.0
                        val powerFactor = (device.powerFactor ?: 1.0).coerceIn(0.8, 1.0)

                        val current = when (device.voltage.type) {
                            VoltageType.AC_1PHASE -> power / (voltage * powerFactor)
                            VoltageType.AC_3PHASE -> power / (1.732 * voltage * powerFactor)
                            VoltageType.DC -> power / voltage
                        }

                        current.takeIf { it.isFinite() } ?: 0.0
                    }
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
