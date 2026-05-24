package ru.mugalimov.volthome.domain.model.singleline

data class SingleLineInputBlock(
    val title: String,
    val incomerLabel: String?,
    val incomerNominalCurrentLabel: String?,
    val totalInstalledPowerWatts: Double?,
    val totalCalculatedPowerWatts: Double?,
    val totalCurrentAmps: Double?
)
