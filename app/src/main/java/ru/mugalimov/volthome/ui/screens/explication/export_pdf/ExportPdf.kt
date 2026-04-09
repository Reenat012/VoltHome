package ru.mugalimov.volthome.ui.screens.explication.export_pdf

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcStep
import ru.mugalimov.volthome.domain.model.CalcWarning
import ru.mugalimov.volthome.domain.model.PlanCapabilities
import ru.mugalimov.volthome.domain.model.report.ReportModel
import ru.mugalimov.volthome.domain.model.report.ReportModel.ReportProfile
import ru.mugalimov.volthome.domain.model.report.ReportModel.PhaseMode as ReportPhaseMode
import ru.mugalimov.volthome.domain.use_case.report.BuildProfessionalSectionsUseCase
import ru.mugalimov.volthome.ui.manual.ForbiddenAction
import ru.mugalimov.volthome.ui.manual.ManualModeGuard
import ru.mugalimov.volthome.ui.utilities.HtmlReportBuilder
import ru.mugalimov.volthome.ui.utilities.PdfPrinter
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.GroupScreenState

@RequiresApi(Build.VERSION_CODES.P)
fun buildExplicationReportHtml(
    activity: Activity,
    vm: ExplicationViewModel,
    caps: PlanCapabilities
): String? {
    val profile: ReportProfile = caps.reportProfile()

    // ✅ ЕДИНЫЙ ИСТОЧНИК ДАННЫХ ДЛЯ PDF:
    // meta/phases + kpi (installed/calculated) берём из слепка ViewModel, а не собираем “альтернативно”.
    val snapshot = vm.buildReportSnapshotForPdf() ?: return null
    val meta = snapshot.meta
    val phases = snapshot.phases

    // phaseMode — из VM (и маппим в enum отчёта)
    val phaseMode: ReportPhaseMode =
        when (vm.phaseMode.value) {
            ru.mugalimov.volthome.domain.model.PhaseMode.SINGLE -> ReportPhaseMode.SINGLE
            ru.mugalimov.volthome.domain.model.PhaseMode.THREE -> ReportPhaseMode.THREE
        }

    val appVersion = resolveAppVersion(activity)

    val reportBase = ReportModel.fromLegacy(
        meta = meta,
        phases = phases,
        profile = profile,
        phaseMode = phaseMode,
        appVersion = appVersion
    )

    val s = vm.uiState.value as? GroupScreenState.Success

    // ✅ Professional sections: строим из тех же meta/phases + предупреждений/допущений из UI state
    val professional = if (profile == ReportProfile.PRO && s != null) {
        val assumptions: List<CalcAssumption> = buildList {
            addAll(s.installedPowerW.assumptions)
            addAll(s.calculatedPowerW.assumptions)
            addAll(s.shieldTotalsAssumptions)
        }.distinctBy { "${it.kind}|${it.source}|${it.subject}|${it.message}|${it.original}|${it.applied}" }

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
                distributionDecisions = emptyList(),
                calcWarnings = warnings,
                assumptions = assumptions
            )
        )
    } else null

    // ✅ KPI: installed/calculated строго из snapshot (== UI state)
    val reportBase2 = reportBase.copy(
        kpis = reportBase.kpis.copy(
            installedPowerW = snapshot.installedPowerW,
            calculatedPowerW = snapshot.calculatedPowerW
        )
    )

    // ✅ Шаги расчёта — из UI state (если PRO)
    val steps: List<CalcStep> = if (profile == ReportProfile.PRO && s != null) {
        buildList {
            addAll(s.installedPowerW.steps)
            addAll(s.calculatedPowerW.steps)
        }
    } else emptyList()

    // ✅ Assumptions — из UI state (если PRO)
    val assumptionsForReport: List<CalcAssumption> =
        if (profile == ReportProfile.PRO && s != null) {
            buildList {
                addAll(s.installedPowerW.assumptions)
                addAll(s.calculatedPowerW.assumptions)
                addAll(s.shieldTotalsAssumptions)
            }.distinctBy { "${it.kind}|${it.source}|${it.subject}|${it.message}|${it.original}|${it.applied}" }
        } else emptyList()

    val reportModel = reportBase2.copy(
        professional = professional,
        steps = steps,
        assumptions = assumptionsForReport,
        warnings = emptyList(),
        normRefs = emptyList()
    )

    val safeModel = ReportModel.sanitizeForProfile(reportModel)

    return HtmlReportBuilder(activity).build(
        model = safeModel,
        includeInlineNormatives = (profile == ReportProfile.PRO)
    )
}

@RequiresApi(Build.VERSION_CODES.P)
private fun resolveAppVersion(activity: Activity): String {
    return try {
        val pm: PackageManager = activity.packageManager
        val pkg = activity.packageName
        val pi = pm.getPackageInfo(pkg, 0)
        val name = pi.versionName ?: ""
        val code = runCatching { pi.longVersionCode.toString() }.getOrElse { "" }
        when {
            name.isNotBlank() && code.isNotBlank() -> "$name ($code)"
            name.isNotBlank() -> name
            else -> code
        }
    } catch (_: Throwable) {
        ""
    }
}

@RequiresApi(Build.VERSION_CODES.P)
fun exportExplicationPdf(
    activity: Activity,
    vm: ExplicationViewModel,
    caps: PlanCapabilities,
    projectId: String,
    manualGuard: ManualModeGuard? = null
) {
    // ✅ Guard: в manual нельзя инициировать PDF без Save/Cancel/Stay
    if (manualGuard != null) {
        manualGuard.request(
            projectId = projectId,
            action = ForbiddenAction.EXPORT_PDF,
            onProceed = {
                val html = buildExplicationReportHtml(activity, vm, caps) ?: return@request
                when (activity) {
                    is ComponentActivity -> activity.lifecycleScope.launch {
                        PdfPrinter(activity).printHtml(html)
                    }
                    else -> activity.runOnUiThread {
                        PdfPrinter(activity).printHtml(html)
                    }
                }
            }
        )
        return
    }

    // fallback (если guard не передали)
    val html = buildExplicationReportHtml(activity, vm, caps) ?: return
    when (activity) {
        is ComponentActivity -> activity.lifecycleScope.launch {
            PdfPrinter(activity).printHtml(html)
        }
        else -> activity.runOnUiThread {
            PdfPrinter(activity).printHtml(html)
        }
    }
}