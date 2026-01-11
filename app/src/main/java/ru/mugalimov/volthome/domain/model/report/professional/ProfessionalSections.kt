package ru.mugalimov.volthome.domain.model.report.professional

/**
 * Профессиональные секции отчёта (PRO only).
 *
 * В Free эти данные не строятся: в [ru.mugalimov.volthome.domain.model.report.ReportModel]
 * поле `professional` должно быть null.
 */
data class ProfessionalSections(
    val evidence: List<ReportEvidenceItem> = emptyList(),
    val warnings: List<ReportWarningItem> = emptyList(),
    val normRefs: List<ReportNormRefItem> = emptyList(),
)