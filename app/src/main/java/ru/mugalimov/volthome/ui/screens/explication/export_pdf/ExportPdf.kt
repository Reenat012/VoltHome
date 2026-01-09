package ru.mugalimov.volthome.ui.screens.explication.export_pdf

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcWarning
import ru.mugalimov.volthome.domain.model.report.ReportModel
import ru.mugalimov.volthome.ui.utilities.HtmlReportBuilder
import ru.mugalimov.volthome.ui.utilities.PdfPrinter
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.GroupScreenState
import ru.mugalimov.volthome.ui.viewmodel.buildReportData

fun exportExplicationPdf(activity: Activity, vm: ExplicationViewModel, isPro: Boolean) {
    val legacy = vm.buildReportData() ?: return
    val (meta, phases) = legacy

    val reportBase = ReportModel.fromLegacy(
        meta = meta,
        phases = phases
    )

    // Берём живые допущения/предупреждения из uiState (они у тебя уже считаются)
    val s = vm.uiState.value as? GroupScreenState.Success

    val assumptions: List<CalcAssumption> = buildList {
        if (s != null) {
            addAll(s.installedPowerW.assumptions)
            addAll(s.calculatedPowerW.assumptions)
            addAll(s.shieldTotalsAssumptions)
        }
    }.distinctBy { it.toString() } // дешёвый дедуп, без знания структуры

    val warnings: List<CalcWarning> = buildList {
        if (s != null) {
            // То, что ты уже считаешь в buildWarningsFromGroups
            addAll(s.calcWarnings)

            // Если в CalculatedValue тоже есть warnings — прокинем
            addAll(s.installedPowerW.warnings)
            addAll(s.calculatedPowerW.warnings)
        }
    }.distinctBy { "${it.severity}|${it.scope}|${it.title}|${it.message}" }

    val reportModel = reportBase.copy(
        assumptions = assumptions,
        warnings = warnings
        // steps = ... (позже)
        // normRefs = ... (позже — сейчас типы конфликтуют)
    )

    val html = HtmlReportBuilder(activity).build(
        model = reportModel,
        isPro = isPro,
        includeProfessionalSections = isPro
    )

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