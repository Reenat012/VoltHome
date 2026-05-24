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
        val html = buildSingleLineDiagramHtmlPlaceholder(diagram)

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

/**
 * Временный HTML-документ для коммита 5.
 *
 * Полноценная отрисовка схемы будет вынесена в SingleLineDiagramRenderer
 * в следующем коммите.
 *
 * Здесь только минимальная оболочка, чтобы:
 * - проверить отдельный PDF flow;
 * - переиспользовать PdfPrinter;
 * - не трогать старый exportExplicationPdf().
 */
private fun buildSingleLineDiagramHtmlPlaceholder(
    diagram: SingleLineDiagram
): String {
    val safeProjectName = diagram.projectName.escapeHtml()

    return """
        <!doctype html>
        <html lang="ru">
        <head>
            <meta charset="utf-8" />
            <style>
                @page {
                    size: A4 portrait;
                    margin: 16mm;
                }

                body {
                    font-family: sans-serif;
                    color: #111827;
                    background: #ffffff;
                    margin: 0;
                    padding: 0;
                }

                .page {
                    width: 100%;
                    box-sizing: border-box;
                }

                .title {
                    font-size: 22px;
                    font-weight: 700;
                    margin-bottom: 8px;
                }

                .subtitle {
                    font-size: 13px;
                    color: #4b5563;
                    margin-bottom: 20px;
                }

                .box {
                    border: 1px solid #d1d5db;
                    border-radius: 10px;
                    padding: 12px;
                    margin-bottom: 12px;
                    page-break-inside: avoid;
                }

                .box-title {
                    font-size: 15px;
                    font-weight: 700;
                    margin-bottom: 6px;
                }

                .text {
                    font-size: 12px;
                    line-height: 1.45;
                    color: #374151;
                }

                .disclaimer {
                    margin-top: 24px;
                    padding-top: 12px;
                    border-top: 1px solid #e5e7eb;
                    font-size: 10px;
                    line-height: 1.45;
                    color: #6b7280;
                }
            </style>
        </head>
        <body>
            <main class="page">
                <div class="title">Однолинейная схема</div>
                <div class="subtitle">$safeProjectName</div>

                <section class="box">
                    <div class="box-title">Статус</div>
                    <div class="text">
                        PDF-flow однолинейной схемы подключён.
                        Полноценная отрисовка схемы будет добавлена отдельным renderer-слоем.
                    </div>
                </section>

                <section class="box">
                    <div class="box-title">Источник данных</div>
                    <div class="text">
                        Схема формируется из готовой экспликации:
                        фазовые секции — ${diagram.phaseSections.size},
                        блоки защиты — ${diagram.protectionBlocks.size}.
                    </div>
                </section>

                <div class="disclaimer">
                    Однолинейная схема сформирована автоматически на основе данных,
                    введённых пользователем в приложении ВольтХом. Материал является
                    инженерной визуализацией и не заменяет проектную документацию,
                    разработанную уполномоченным специалистом.
                </div>
            </main>
        </body>
        </html>
    """.trimIndent()
}

/**
 * Минимальное экранирование текста перед вставкой в HTML.
 */
private fun String.escapeHtml(): String {
    return this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}