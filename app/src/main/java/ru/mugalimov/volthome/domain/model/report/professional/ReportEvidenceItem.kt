package ru.mugalimov.volthome.domain.model.report.professional

/**
 * Пункт обоснования/доказательной базы в отчёте.
 *
 * Формат intentionally простой: короткий заголовок + 1–3 строки фактов.
 */
data class ReportEvidenceItem(
    val id: String,
    val title: String,
    val body: String,
)