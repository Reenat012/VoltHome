package ru.mugalimov.volthome.domain.model.report.professional

/**
 * Справочная нормативная ссылка в отчёте.
 *
 * Важно:
 * - поле [section] заполняется только если пункт/раздел реально известен системе.
 * - никаких длинных цитат.
 */
data class ReportNormRefItem(
    val id: String,
    val source: String,
    val section: String? = null,
    val note: String? = null,
)