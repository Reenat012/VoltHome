package ru.mugalimov.volthome.ui.screens.explication.export_pdf

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcWarning
import ru.mugalimov.volthome.domain.model.PlanCapabilities
import ru.mugalimov.volthome.domain.model.report.ReportModel
import ru.mugalimov.volthome.domain.use_case.report.BuildProfessionalSectionsUseCase
import ru.mugalimov.volthome.ui.utilities.HtmlReportBuilder
import ru.mugalimov.volthome.ui.utilities.PdfPrinter
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.GroupScreenState
import ru.mugalimov.volthome.ui.viewmodel.buildReportData

/**
 * Строит HTML отчёта для превью/экспорта.
 *
 * ВАЖНО:
 * - Эта функция НЕ проверяет pdfExport.
 * - Preview доступен в Free и PRO.
 * - Ограничения экспорта должны применяться выше, в export-actions path.
 */
fun buildExplicationReportHtml(
    activity: Activity,
    vm: ExplicationViewModel,
    caps: PlanCapabilities
): String? {
    // Данные отчёта: доступны в Free и PRO (gate по pdfExport тут запрещён).
    val legacy = vm.buildReportData() ?: return null
    val (meta, phases) = legacy

    val reportBase = ReportModel.fromLegacy(
        meta = meta,
        phases = phases
    )

    // Берём живые данные из uiState (они у тебя уже считаются)
    val s = vm.uiState.value as? GroupScreenState.Success

    val professional = if (caps.professionalReportSections && s != null) {
        val assumptions: List<CalcAssumption> = buildList {
            addAll(s.installedPowerW.assumptions)
            addAll(s.calculatedPowerW.assumptions)
            addAll(s.shieldTotalsAssumptions)
        }.distinctBy { it.toString() }

        val warnings: List<CalcWarning> = buildList {
            addAll(s.calcWarnings)
            addAll(s.installedPowerW.warnings)
            addAll(s.calculatedPowerW.warnings)
        }.distinctBy { "${it.severity}|${it.scope}|${it.title}|${it.message}" }

        BuildProfessionalSectionsUseCase().execute(
            BuildProfessionalSectionsUseCase.Params(
                phaseMode = vm.phaseMode.value,
                meta = meta,
                phases = phases,
                distributionDecisions = emptyList(), // decisions подключим позже, если нужно
                calcWarnings = warnings,
                assumptions = assumptions
            )
        )
    } else {
        null
    }

    val reportBase2 = if (s != null) {
        reportBase.copy(
            kpis = reportBase.kpis.copy(
                installedPowerW = s.installedPowerW.value,
                calculatedPowerW = s.calculatedPowerW.value
            )
        )
    } else {
        reportBase
    }

    val reportModel = reportBase2.copy(
        professional = professional,
        assumptions = emptyList(),
        warnings = emptyList(),
        steps = emptyList(),
        normRefs = emptyList()
    )

    // HTML: inline-нормативы только в PRO (это про содержание, а не про доступность preview).
    return HtmlReportBuilder(activity).build(
        model = reportModel,
        includeInlineNormatives = caps.professionalReportSections
    )
}

/**
 * Временный экспорт через системный Print UI.
 *
 * ВАЖНО:
 * - Этот метод делает печать/экспорт (то есть действия).
 * - Gate по pdfExport должен жить ВЫШЕ (в VM / export-actions path), не здесь.
 */
fun exportExplicationPdf(
    activity: Activity,
    vm: ExplicationViewModel,
    caps: PlanCapabilities
) {
    val html = buildExplicationReportHtml(activity, vm, caps) ?: return

    when (activity) {
        is ComponentActivity -> {
            activity.lifecycleScope.launch {
                PdfPrinter(activity).printHtml(html)
            }
        }

        else -> {
            activity.runOnUiThread {
                PdfPrinter(activity).printHtml(html)
            }
        }
    }
}