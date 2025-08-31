package ru.mugalimov.volthome.domain.model

data class PhaseBalance(
    val pct: Double,          // перекос в %, 0..100
    val maxPhase: Phase,
    val minPhase: Phase,
    val avg: Double
)
