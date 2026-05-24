package ru.mugalimov.volthome.domain.model.singleline

import ru.mugalimov.volthome.domain.model.PhaseMode

/**
 * Корневая доменная модель однолинейной схемы.
 *
 * Важно:
 * эта модель не выполняет расчёты, не выбирает автоматы,
 * не подбирает кабели и не меняет структуру экспликации.
 */
data class SingleLineDiagram(
    val projectName: String,
    val phaseMode: PhaseMode,
    val input: SingleLineInputBlock,
    val protectionBlocks: List<SingleLineProtectionBlock>,
    val phaseSections: List<SingleLinePhaseSection>,
    val neutralBus: SingleLineBus,
    val protectiveEarthBus: SingleLineBus,
    val generatedAtMillis: Long
)