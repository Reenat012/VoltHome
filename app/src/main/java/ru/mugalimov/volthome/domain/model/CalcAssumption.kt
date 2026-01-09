package ru.mugalimov.volthome.domain.model

/**
 * Допущение/подмена/нормализация входных данных, влияющая на результат.
 * Нужна, чтобы "магия" стала явной.
 */
data class CalcAssumption(
    val kind: Kind,
    /** Источник применённого значения (если релевантно) */
    val source: CoefficientSource? = null,
    /** Какое поле/параметр затронут: "demandRatio", "powerFactor", "U", и т.п. */
    val subject: String,
    /** Человекочитаемое описание (коротко, инженерно) */
    val message: String,
    /** Оригинальное значение (если применимо) */
    val original: Double? = null,
    /** Применённое значение (если применимо) */
    val applied: Double? = null
) {
    enum class Kind {
        /** Использовано значение по умолчанию из-за отсутствия пользовательского */
        DEFAULT_USED,
        /** Значение было скорректировано (зажато/округлено/заменено) */
        NORMALIZED,
        /** Применён упрощённый расчёт/модель */
        SIMPLIFIED,
        /** Другое явное допущение */
        OTHER
    }
}