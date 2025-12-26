package ru.mugalimov.volthome.ui.components.device.adapter

data class DeviceParamsErrors(
    val powerError: String? = null,
    val powerFactorError: String? = null,
    val demandRatioError: String? = null,
)