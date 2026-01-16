package ru.mugalimov.volthome.domain.model

/**
 * Один шаг расчёта: что посчитали, по какой формуле, какие входы, что получили.
 * Это НЕ "учебник", а короткий инженерный протокол.
 */
data class CalcStep(
    /** Короткое имя шага: "Ток группы", "Расчётная мощность", "Подбор автомата" */
    val name: String,
    /**
     * Формула/выражение в виде строки.
     * Пример: "I = P / (U * cosφ)", "Pрасч = Pуст * demandRatio"
     */
    val formula: String,
    /** Численные входы формулы (именованные) */
    val inputs: List<CalcInput> = emptyList(),
    /** Численный результат шага */
    val output: CalcOutput,
    /** Нормативные ссылки, если есть */
    val normRefs: List<NormRef> = emptyList(),
    /** Допущения, которые применились именно в этом шаге */
    val assumptions: List<CalcAssumption> = emptyList()
)

data class CalcInput(
    /**
     * Имя входа.
     *
     * Соглашение для "прозрачных" групповых шагов (если входы плоские):
     *  - "<deviceName> / base"   — исходное значение (Pуст или Iном) для устройства
     *  - "<deviceName> / k"      — коэффициент спроса (kспроса)
     *  - "<deviceName> / result" — вклад устройства с учётом спроса (Pрасч или Iрасч)
     */
    val name: String,
    val value: Double,
    /** Например: "Вт", "кВт", "В", "А", "", "%" */
    val unit: String = ""
)

data class CalcOutput(
    val value: Double,
    val unit: String = ""
)