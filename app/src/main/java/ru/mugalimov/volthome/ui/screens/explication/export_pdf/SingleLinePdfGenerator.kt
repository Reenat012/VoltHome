package ru.mugalimov.volthome.ui.screens.explication.export_pdf

import android.app.Activity
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.singleline.SingleLineDiagram
import ru.mugalimov.volthome.ui.manual.ForbiddenAction
import ru.mugalimov.volthome.ui.manual.ManualModeGuard
import ru.mugalimov.volthome.ui.utilities.PdfPrinter

/**
 * Отдельный generator PDF для однолинейной схемы.
 *
 * Важно:
 * - не меняет старый PDF-отчёт;
 * - не создаёт отдельную PDF-инфраструктуру;
 * - использует существующий PdfPrinter;
 * - не обещает URI/файл, потому что текущий pipeline работает через Android print flow.
 */
@RequiresApi(Build.VERSION_CODES.P)
fun exportSingleLineDiagramPdf(
    activity: Activity,
    diagram: SingleLineDiagram,
    projectId: String,
    manualGuard: ManualModeGuard? = null
) {
    val exportAction: () -> Unit = {
        val html = SingleLineDiagramRenderer.render(diagram)

        when (activity) {
            is ComponentActivity -> {
                activity.lifecycleScope.launch {
                    PdfPrinter(activity).printHtml(html)
                }
                Unit
            }

            else -> {
                activity.runOnUiThread {
                    PdfPrinter(activity).printHtml(html)
                }
                Unit
            }
        }
    }

    // Используем тот же manual-guard, что и старый PDF export.
    // Это важно: экспорт — внешнее действие, его нельзя запускать поверх незавершённой manual-сессии.
    if (manualGuard != null) {
        manualGuard.request(
            projectId = projectId,
            action = ForbiddenAction.EXPORT_PDF,
            onProceed = exportAction
        )
        return
    }

    exportAction()
}
