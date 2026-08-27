package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.ProjectCoverage
import java.util.Locale

/** Чистая часть проверки: каждое устройство проекта должно входить ровно в расчётный охват. */
object ProjectCoverageCalculator {
    fun calculate(
        devices: List<Device>,
        assignedDeviceIds: Set<Long>
    ): ProjectCoverage {
        val unassigned = devices
            .filterNot { it.id in assignedDeviceIds }
            .sortedBy { it.name.lowercase(Locale.ROOT) }

        return ProjectCoverage(
            totalDeviceCount = devices.size,
            assignedDeviceCount = devices.count { it.id in assignedDeviceIds },
            unassignedDevices = unassigned
        )
    }
}
