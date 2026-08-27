package ru.mugalimov.volthome.domain.model

/** Покрытие всех устройств проекта расчётными группами. */
data class ProjectCoverage(
    val totalDeviceCount: Int = 0,
    val assignedDeviceCount: Int = 0,
    val unassignedDevices: List<Device> = emptyList()
) {
    val unassignedCount: Int get() = unassignedDevices.size
    val isComplete: Boolean get() = unassignedDevices.isEmpty()

    companion object {
        val Empty = ProjectCoverage()
    }
}
