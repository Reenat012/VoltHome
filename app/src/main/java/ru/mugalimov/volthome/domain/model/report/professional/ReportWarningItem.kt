package ru.mugalimov.volthome.domain.model.report.professional

/**
 * Предупреждение в профессиональном отчёте.
 */
data class ReportWarningItem(
    val id: String,
    val severity: Severity,
    val title: String,
    val message: String,
    val scope: String? = null,
) {
    enum class Severity {
        INFO,
        WARNING,
        CRITICAL,
    }
}