package ru.mugalimov.volthome.domain.model.singleline

import ru.mugalimov.volthome.domain.model.Phase

/**
 * Блок одной группы щита на однолинейной схеме.
 *
 * Значения берутся из готовой экспликации.
 * Модель не содержит логики выбора автомата, кабеля или УЗО.
 */
data class SingleLineGroupBlock(
    val groupId: Long,
    val groupNumber: Int,
    val groupName: String,
    val phase: Phase?,
    val roomNames: List<String>,
    val devices: List<SingleLineDeviceSummary>,
    val installedPowerWatts: Double?,
    val calculatedPowerWatts: Double?,
    val calculatedCurrentAmps: Double?,
    val breakerLabel: String?,
    val cableLabel: String?,
    val rcdLabel: String?,
    val leakageCurrentMilliAmps: Int?,
    val warnings: List<String>
)