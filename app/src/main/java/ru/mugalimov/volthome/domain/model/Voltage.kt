package ru.mugalimov.volthome.domain.model

data class Voltage(
    val value: Int,          // Числовое значение (220, 380 и т.д.)
    val type: VoltageType    // Тип напряжения (AC_DC, PHASES)
)