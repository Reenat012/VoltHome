// app/src/main/java/ru/mugalimov/volthome/ui/utilities/HtmlReportBuilder.kt
package ru.mugalimov.volthome.ui.utilities

import android.content.Context
import ru.mugalimov.volthome.domain.model.report.ReportMeta
import ru.mugalimov.volthome.domain.model.report.ReportPhase

/**
 * Заменяет блок вида {{#sectionName}} ... {{/sectionName}} на replacement.
 * Если теги не найдены — возвращает html без изменений.
 */
private fun replaceSection(
    html: String,
    sectionName: String,
    replacement: String
): String {
    val startTag = "{{#$sectionName}}"
    val endTag = "{{/$sectionName}}"

    val startIndex = html.indexOf(startTag)
    if (startIndex == -1) return html // секция не найдена

    val endIndex = html.indexOf(endTag, startIndex)
    if (endIndex == -1) return html // закрывающий тег не найден

    return buildString(html.length - (endIndex + endTag.length - startIndex) + replacement.length) {
        append(html, 0, startIndex)                          // всё до секции
        append(replacement)                                  // наш контент
        append(html, endIndex + endTag.length, html.length)  // всё после секции
    }
}

class HtmlReportBuilder(private val context: Context) {

    private fun loadAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Шаблон лежит в app/src/main/assets/report_pdf/report.html */
    private fun template(): String {
        // основной путь
        return try {
            loadAsset("report_pdf/report.html")
        } catch (e: Exception) {
            // запасной — если вдруг перенесёшь шаблон
            try {
                loadAsset("report.html")
            } catch (_: Exception) {
                // минимальный fallback, чтобы не падать
                """
                <!doctype html><meta charset="utf-8">
                <title>VoltHome — Экспликация</title>
                <style>
                  body{font-family:sans-serif;padding:16px}
                  h2{margin-top:16px}
                  table{width:100%;border-collapse:collapse}
                  td{padding:6px;vertical-align:top}
                  .chip{display:inline-block;padding:2px 6px;border:1px solid #ddd;border-radius:6px}
                </style>
                <h1>VoltHome — Экспликация</h1>
                <div>Дата: <b>{{date}}</b></div>
                <div>Вводной аппарат: <b>{{incomer}}</b></div>
                <div>Всего групп: <b>{{totalGroups}}</b></div>
                <div>Суммарный ток: <b>{{totalCurrent}} А</b></div>
                <div>Токи по фазам: <b>A: {{iA}} А · B: {{iB}} А · C: {{iC}} А</b></div>
                {{#donut}}<h2>Диаграмма</h2><img src="{{donut}}"/>{{/donut}}
                <h2>Экспликация</h2>
                {{#phases}}<h3>{{name}}</h3>{{rows}}{{/phases}}
                """.trimIndent()
            }
        }
    }

    /**
     * @param donutDataUri "data:image/png;base64,..." или null, если диаграмму не вставляем
     */
    fun build(
        meta: ReportMeta,
        phases: List<ReportPhase>,
        donutDataUri: String?,
        isPro: Boolean
    ): String {
        var html = template()

        fun rep(k: String, v: String) { html = html.replace("{{$k}}", v) }

        // плейсхолдеры
        rep("date", meta.date)
        rep("incomer", meta.incomer)
        rep("totalGroups", meta.totalGroups.toString())
        rep("totalCurrent", "%.1f".format(meta.totalCurrent))
        rep("iA", "%.1f".format(meta.phaseCurrents["A"] ?: 0.0))
        rep("iB", "%.1f".format(meta.phaseCurrents["B"] ?: 0.0))
        rep("iC", "%.1f".format(meta.phaseCurrents["C"] ?: 0.0))

        // Донат
        html = if (donutDataUri.isNullOrBlank()) {
            replaceSection(html, "donut", "")
        } else {
            val donutSection = """
          <h2>Распределение нагрузки по фазам</h2>
          <img src="$donutDataUri" alt="Диаграмма фаз" style="max-width:100%;"/>
        """.trimIndent()
            replaceSection(html, "donut", donutSection)
        }

        // Водяной знак (только Free)
        html = if (isPro) replaceSection(html, "watermark", "")
        else replaceSection(html, "watermark", """<div class="watermark"><img src="img/logo.png" alt="VoltHome"/></div>""")

        // Фазы→группы→устройства
        val phasesHtml = buildString {
            phases.forEach { phase ->
                append("<div class=\"section\">")
                append("<h2>${phase.name}</h2>")
                append("<table class=\"table\">")
                phase.groups.forEach { g ->
                    append("<tr class=\"row\"><td width=\"36%\"><span class=\"chip\">${g.title}</span></td><td>")
                    g.devices.forEach { d -> append("<div class=\"li\">• ${d.name} — ${d.spec}</div>") }
                    append("</td></tr>")
                }
                append("</table>")
                append("</div>")
            }
        }
        html = replaceSection(html, "phases", phasesHtml)

        return html
    }
}