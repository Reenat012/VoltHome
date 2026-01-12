package ru.mugalimov.volthome.domain.use_case.report

import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcWarning
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.report.ReportMeta
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import ru.mugalimov.volthome.domain.model.report.professional.ProfessionalSections
import ru.mugalimov.volthome.domain.model.report.professional.ReportNormRefItem
import ru.mugalimov.volthome.domain.model.report.professional.ReportWarningItem

/**
 * Единая точка правды для PRO-секций отчёта:
 * evidence / warnings / normRefs.
 *
 * Важно: без пересчётов — только агрегирование фактов, которые уже есть.
 */
class BuildProfessionalSectionsUseCase {

    data class Params(
        val phaseMode: PhaseMode,
        val meta: ReportMeta,
        val phases: List<ReportPhase>,
        val distributionDecisions: List<DistributionDecision> = emptyList(),
        val calcWarnings: List<CalcWarning> = emptyList(),
        val assumptions: List<CalcAssumption> = emptyList(),
    )

    fun execute(params: Params): ProfessionalSections {
        return ProfessionalSections(
            warnings = buildWarnings(params.calcWarnings),
            normRefs = buildNormRefs()
        )
    }

    private fun buildWarnings(src: List<CalcWarning>): List<ReportWarningItem> {
        if (src.isEmpty()) return emptyList()

        return src.mapIndexed { idx, w ->
            ReportWarningItem(
                id = "w_$idx",
                severity = w.severity.toReportSeverity(),
                title = w.title,
                message = w.message,
                scope = w.scope
            )
        }
    }

    private fun buildNormRefs(): List<ReportNormRefItem> {
        // Справочно, без пунктов (не придумываем номера).
        return listOf(
            ReportNormRefItem(
                id = "nr_pue",
                source = "ПУЭ",
                section = null,
                note = "Справочно (без точных пунктов)"
            ),
            ReportNormRefItem(
                id = "nr_sp256",
                source = "СП 256.1325800.2016",
                section = null,
                note = "Справочно (без точных пунктов)"
            ),
            ReportNormRefItem(
                id = "nr_gost_iec",
                source = "ГОСТ Р 50571 / IEC 60364",
                section = null,
                note = "Справочно (без точных пунктов)"
            ),
        )
    }

    private fun CalcWarning.Severity.toReportSeverity(): ReportWarningItem.Severity =
        when (this) {
            CalcWarning.Severity.INFO -> ReportWarningItem.Severity.INFO
            CalcWarning.Severity.WARNING -> ReportWarningItem.Severity.WARNING
            CalcWarning.Severity.CRITICAL -> ReportWarningItem.Severity.CRITICAL
        }

    private fun formatA(value: Double): String =
        String.format(java.util.Locale.US, "%.1fA", value)
}