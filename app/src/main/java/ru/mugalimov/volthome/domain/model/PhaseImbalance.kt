package ru.mugalimov.volthome.domain.model

data class PhaseImbalance(
    val iA: Double, val iB: Double, val iC: Double,
    val iMax: Double, val iMin: Double, val iAvg: Double,
    val delta: Double,          // ΔI = Imax - Imin
    val pct: Double,            // % = ΔI / Iavg * 100
    val worstPhase: Phase       // фаза с Imax
)
