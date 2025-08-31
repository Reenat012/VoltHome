package ru.mugalimov.volthome.domain.model.phase_load

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device

data class GroupWithDevices(
    val group: CircuitGroup,
    val devices: List<Device>
)

