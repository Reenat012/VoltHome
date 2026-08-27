package ru.mugalimov.volthome.domain.model.singleline

import ru.mugalimov.volthome.domain.model.Phase

/**
 * Блок защитного аппарата на однолинейной схеме.
 *
 * Используется только для отображения уже известных данных.
 * Дифзащита, УЗИП и реле напряжения отображаются только если такие данные
 * реально пришли из экспликации или будущего источника данных.
 */
data class SingleLineProtectionBlock(
    val id: String,
    val title: String,
    val type: SingleLineProtectionType,
    val phase: Phase?,
    val nominalCurrentAmps: Double?,
    val leakageCurrentMilliAmps: Int?,
    val description: String?,
    val warning: String? = null
)

/**
 * Тип защитного блока для PDF-визуализации.
 */
enum class SingleLineProtectionType {
    INPUT_BREAKER,
    VOLTAGE_RELAY,
    PHASE_CONTROL_RELAY,
    CURRENT_RELAY,
    MODULAR_CONTACTOR,
    SURGE_PROTECTION,
    RCD,
    DIFF_BREAKER,
    GROUP_BREAKER,
    UNKNOWN
}
