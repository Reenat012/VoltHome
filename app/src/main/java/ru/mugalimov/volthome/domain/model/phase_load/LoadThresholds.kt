package ru.mugalimov.volthome.domain.model.phase_load

/** Настройка порогов из UI, % от I_incomer */
data class LoadThresholds(
    val warnPct: Int = 60,  // зелёная зона: ≤ warn
    val alertPct: Int = 80  // жёлтая: (warn..alert), красная: ≥ alert
)