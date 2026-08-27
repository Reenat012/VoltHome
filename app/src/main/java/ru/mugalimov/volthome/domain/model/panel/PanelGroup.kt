package ru.mugalimov.volthome.domain.model.panel

import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.cable.CableCalculationStatus

/**
 * Группа, связанная с аппаратом защиты
 * в визуализации щита.
 *
 * Содержит только уже рассчитанные данные, нужные для подписи аппарата и
 * информационного листа. Монтажные координаты сюда не попадают.
 */
data class PanelGroup(
    val id: Long,
    val number: Int,
    val roomName: String,
    val type: DeviceType,
    val cableSection: Double,
    val cableLabel: String,
    val cableStatus: CableCalculationStatus?,
    val voltageDropPercent: Double?,
    val correctedAmpacityA: Double?,
    val phase: Phase,
    val installedPowerW: Int,
    val installedCurrentA: Double,
    val circuitBreaker: Int,
    val breakerType: String,
    val rcdRequired: Boolean,
    val rcdCurrent: Int,
    val rcdReasonCodes: List<String>,
    val calculationSource: CalculationSource,
    val algorithmVersion: Int,
    val deviceNames: List<String>
) {
    val shortLabel: String
        get() = "Гр. $number"
}
