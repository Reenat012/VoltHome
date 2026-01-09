package ru.mugalimov.volthome.domain.model

/**
 * Итоговое рассчитанное значение + доказательная база:
 * - шаги (CalcStep)
 * - допущения (CalcAssumption)
 * - предупреждения (CalcWarning)
 * - нормативные ссылки (NormRef)
 */
data class CalculatedValue(
    val value: Double,
    /** Например: "А", "Вт", "кВт" */
    val unit: String,
    /** Короткая подпись: "Ток группы", "Расчётная мощность" */
    val label: String? = null,
    val steps: List<CalcStep> = emptyList(),
    val assumptions: List<CalcAssumption> = emptyList(),
    val warnings: List<CalcWarning> = emptyList(),
    val normRefs: List<NormRef> = emptyList()
)