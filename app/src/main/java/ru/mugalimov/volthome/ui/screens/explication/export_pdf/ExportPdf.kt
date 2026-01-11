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

fun exportExplicationPdf(activity: Activity, vm: ExplicationViewModel,  caps: PlanCapabilities) {
    val legacy = vm.buildReportData() ?: return
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
                assumptions = assumptions,
            )
        )
    } else {
        null
    }

// ВАЖНО: warnings/assumptions больше НЕ дублируем в legacy полях ReportModel
    val reportModel = reportBase.copy(
        professional = professional,
        assumptions = emptyList(),
        warnings = emptyList(),
        steps = emptyList(),
        normRefs = emptyList()
    )

    val html = HtmlReportBuilder(activity).build(
        model = reportModel,
        includeProfessionalSections = (caps.professionalReportSections && professional != null)
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