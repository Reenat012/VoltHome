package ru.mugalimov.volthome.ui.screens.explication.export_pdf

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.utilities.*
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.GroupScreenState
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.use_case.getOrZero
import ru.mugalimov.volthome.domain.use_case.phaseCurrents
import ru.mugalimov.volthome.ui.viewmodel.buildReportData

fun exportExplicationPdf(activity: ComponentActivity, vm: ExplicationViewModel) {
    val pair = vm.buildReportData() ?: return
    val (meta, phases) = pair

    activity.lifecycleScope.launch {
        // Рендер доната из твоего компонента, с теми же данными
        val state = vm.uiState.value as? GroupScreenState.Success
        val perPhase = state?.groups?.let { phaseCurrents(it) } ?: emptyMap()
        val donutB64 = renderComposableToBase64Png(
            activity = activity, widthPx = 1080, heightPx = 1080
        ) {
            ru.mugalimov.volthome.ui.screens.loads.PhaseLoadDonutChart(
                perPhase = mapOf(
                    Phase.A to (perPhase.getOrZero(Phase.A)),
                    Phase.B to (perPhase.getOrZero(Phase.B)),
                    Phase.C to (perPhase.getOrZero(Phase.C))
                ),
                showLegend = true
            )
        }

        val html = HtmlReportBuilder(activity).build(
            meta = meta,
            phases = phases,
            donutDataUri = donutB64,
            isPro = /* vm.isPro */ false // <- поставь true/false как нужно
        )
        PdfPrinter(activity).printHtml(html)
    }
}