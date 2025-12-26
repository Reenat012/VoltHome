package ru.mugalimov.volthome.ui.components.device.adapter

import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.VoltageType

data class DeviceParamsDraft(
    val name: String,
    val powerText: String,

    // PRO
    val deviceType: DeviceType,
    val powerFactorText: String,
    val demandRatioText: String,
    val voltageType: VoltageType,
    val hasMotor: Boolean,
    val requiresDedicatedCircuit: Boolean,
    val requiresSocketConnection: Boolean,
)