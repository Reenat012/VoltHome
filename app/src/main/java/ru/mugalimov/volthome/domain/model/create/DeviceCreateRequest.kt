package ru.mugalimov.volthome.domain.model.create

import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage

data class DeviceCreateRequest(
    val title: String,
    val type: DeviceType,
    val count: Int = 1,
    val ratedPowerW: Int? = null,
    val powerFactor: Double? = null,
    val demandRatio: Double? = null,
    val voltage: Voltage? = null
)
