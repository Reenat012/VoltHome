package ru.mugalimov.volthome.domain.use_case.report

import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcWarning
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.report.ReportMeta
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import ru.mugalimov.volthome.domain.model.report.professional.ProfessionalSections
import ru.mugalimov.volthome.domain.model.report.professional.ReportEvidenceItem
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
            evidence = buildEvidence(params),
            warnings = buildWarnings(params.calcWarnings),
            normRefs = buildNormRefs()
        )
    }

    private fun buildEvidence(p: Params): List<ReportEvidenceItem> {
        val out = mutableListOf<ReportEvidenceItem>()

        // 1) Режим сети
        out += ReportEvidenceItem(
            id = "phase_mode",
            title = "Режим сети",
            body = when (p.phaseMode) {
                PhaseMode.SINGLE -> "1φ (однофазная сеть)"
                PhaseMode.THREE -> "3φ (трёхфазная сеть)"
            }
        )

        // 2) Вводной аппарат (как факт выбора)
        val incomer = p.meta.incomerLabel?.takeIf { it.isNotBlank() } ?: "—"
        out += ReportEvidenceItem(
            id = "incomer",
            title = "Вводной аппарат",
            body = incomer
        )

        // 3) Итоговые токи (из meta)
        val currents = p.meta.headlineCurrents.entries
            .sortedBy { it.key }
            .joinToString(", ") { (k, v) -> "$k=${formatA(v)}" }
        out += ReportEvidenceItem(
            id = "headline_currents",
            title = "Итоговые токи",
            body = currents
        )

        // 4) Структура щита (по ReportPhase/ReportGroup/ReportDevice)
        val groupsCount = p.phases.sumOf { it.groups.size }
        val devicesCount = p.phases.sumOf { ph -> ph.groups.sumOf { it.devices.size } }
        out += ReportEvidenceItem(
            id = "structure",
            title = "Структура расчёта",
            body = "Группы: $groupsCount, устройства: $devicesCount"
        )

        // 5) Наличие подобранных labels (как факт)
        val nonEmptySwitch = p.phases.flatMap { it.groups }.count { !it.switchLabel.isNullOrBlank() }
        val nonEmptyCable = p.phases.flatMap { it.groups }.count { !it.cableLabel.isNullOrBlank() }
        out += ReportEvidenceItem(
            id = "selection_labels",
            title = "Подбор по группам",
            body = "Автоматы: $nonEmptySwitch/$groupsCount, кабели: $nonEmptyCable/$groupsCount"
        )

        // 6) Фазное распределение (только факт наличия decision log)
        if (p.phaseMode == PhaseMode.THREE) {
            val decisions = p.distributionDecisions.size
            out += ReportEvidenceItem(
                id = "phase_distribution",
                title = "Распределение по фазам",
                body = if (decisions > 0) {
                    "Распределено групп: $decisions (decision log доступен)"
                } else {
                    "Распределение выполнено (decision log не передан в отчёт)"
                }
            )
        }

        // 7) Допущения (как факт применения)
        if (p.assumptions.isNotEmpty()) {
            val sample = p.assumptions.take(2).joinToString("; ") { it.message }
            out += ReportEvidenceItem(
                id = "assumptions",
                title = "Допущения модели",
                body = buildString {
                    append("Допущений: ${p.assumptions.size}.")
                    if (sample.isNotBlank()) append(" Примеры: $sample")
                }
            )
        }

        // DoD 5–8: в типовом проекте уже есть 5 пунктов (+1 для 3φ, +1 при assumptions).
        return out
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