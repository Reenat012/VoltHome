package ru.mugalimov.volthome.ui.screens.explication.export_pdf

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.utilities.HtmlReportBuilder
import ru.mugalimov.volthome.ui.utilities.PdfPrinter
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.buildReportData

/**
 * Экспорт отчёта экспликации в PDF.
 * HTML формируется с учётом режима сети (1/3 фазы).
 */
fun exportExplicationPdf(activity: Activity, vm: ExplicationViewModel, isPro: Boolean) {
    val data = vm.buildReportData() ?: return
    val (meta, phases) = data

    val html = HtmlReportBuilder(activity).build(
        meta = meta,
        phases = phases,
        isPro = isPro
    )

    when (activity) {
        is ComponentActivity -> {
            activity.lifecycleScope.launch {
                PdfPrinter(activity).printHtml(html)
            }
        }

        else -> {
            // На случай, если это не ComponentActivity: обеспечим вызов с UI-потока.
            activity.runOnUiThread {
                PdfPrinter(activity).printHtml(html)
            }
        }
    }
}
