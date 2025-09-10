package ru.mugalimov.volthome.ui.utilities

import android.content.Context
import androidx.annotation.WorkerThread
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.report.DonutModel
import ru.mugalimov.volthome.domain.model.report.ReportDevice
import ru.mugalimov.volthome.domain.model.report.ReportGroup
import ru.mugalimov.volthome.domain.model.report.ReportMeta
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.*

class HtmlReportBuilder(private val context: Context) {

    private val ruSymbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
        decimalSeparator = ','
        groupingSeparator = ' '
    }
    private val df2 = DecimalFormat("#,##0.00", ruSymbols) // ток — сотые
    private val df1 = DecimalFormat("#,##0.0", ruSymbols)
    private val df0 = DecimalFormat("#,##0", ruSymbols)

    private val UNIT_A = "\u0410" // кириллическая «А»
    private val VOLTAGE_DEFAULT = 230.0
    private val eps = 1e-6

    // Алиасы для чтения числовых полей из ReportDevice
    private val powerFieldAliases = listOf("powerW", "power", "watt", "pW")
    private val currentFieldAliases = listOf("currentA", "amp", "a")

    // Алиасы для мета-информации групп
    private val groupSwitchAliases = listOf(
        "switchLabel", "groupSwitchLabel", "apparatusLabel",
        "protectionLabel", "breakerLabel", "rcdLabel", "deviceLabel"
    )
    private val groupCableAliases = listOf("cableLabel", "lineLabel", "wireLabel", "cableInfo", "lineInfo")

    // Вставляемый CSS: футер в потоке, запрет разрывов внутри группы, устранение двойных линий
    private val INLINE_STYLE = """
        <style>
          /* Резерв под футер (футер в потоке, не fixed) */
          body { padding-bottom: 96px; }

          /* Футер как обычный блок внизу документа */
          .footer {
            margin-top: 24px;
            padding-top: 8px;
            border-top: 1px solid #E5E7EB;
            font-size: 11px;
            color: #6B7280;
          }

          /* Страховочный спейсер (если используется плейсхолдер {{footerSpacer}}) */
          .footer-spacer { height: 72px; }

          /* Таблица по фазе — базовые границы */
          .phase-table { width: 100%; border-collapse: collapse; }
          .phase-table th, .phase-table td { padding: 10px 0; }

          /* Каждая группа — неделимый блок, чтобы шапка/мета не отрывались от устройств */
          tbody.group-block { break-inside: avoid; page-break-inside: avoid; }

          /* Единый разделитель между группами */
          .phase-table td, .phase-table th { border-bottom: 1px solid #E5E7EB; }
          thead tr.hdr th { border-bottom-width: 1px; }

          /* Убираем дублирование линий: у последнего устройства группы нет нижней границы,
             у шапки группы рисуем верхнюю границу как разделитель между группами */
          tbody.group-block tr.dev:last-child td { border-bottom: 0; }
          tbody.group-block tr.group-row td { border-top: 1px solid #E5E7EB; border-bottom: 0; }
          tbody.group-block tr.group-meta td { border-bottom: 1px solid #E5E7EB; }

          /* Подстраховка разрывов у ключевых строк */
          tr.group-row, tr.group-meta, tr.hdr, tr.dev {
            break-inside: avoid; page-break-inside: avoid;
          }

          /* Вспомогательные выравнивания */
          th.center, td.center { text-align: left; }
          th.num, td.num { text-align: right; white-space: nowrap; }
          .chip { display:inline-block; padding:4px 10px; border-radius:9999px; background:#F3F4F6; }
          .h2-tight { margin: 0 0 8px 0; }
        </style>
    """.trimIndent()

    @WorkerThread
    fun build(
        meta: ReportMeta,
        phases: List<ReportPhase>,
        isPro: Boolean = false
    ): String {
        // template.html → report.html → fallback
        var html = runCatching { loadTemplate("report_pdf/template.html") }
            .recoverCatching { loadTemplate("report_pdf/report.html") }
            .getOrElse { FALLBACK_TEMPLATE }

        // Инъекция CSS перед </head> (если в шаблоне есть <head>)
        html = if (html.contains("</head>", ignoreCase = true)) {
            html.replace(Regex("</head>", RegexOption.IGNORE_CASE), "$INLINE_STYLE</head>")
        } else {
            // если head отсутствует, просто префиксуем стилем
            "$INLINE_STYLE$html"
        }

        // Шапка
        html = html.replace("{{projectName}}", "")
        html = html.replace("{{date}}", escape(meta.date))
        html = html.replace("{{kpiBlock}}", buildKpiBlock(meta))

        // Донат + легенда
        html = html.replace("{{donutSection}}", buildDonutSection(meta.donut))
        html = html.replace("{{legendSection}}", buildLegend(meta.donut))

        // Экспликация
        val phasesHtml = buildPhasesTables(phases)
        html = html.replace("{{phasesHtml}}", phasesHtml)
        html = html.replace("{{phases}}", phasesHtml)

        // Водяной знак
        html = if (!isPro) {
            html.replace(
                "{{watermark}}",
                """<div class="watermark"><img src="img/logo.png" alt="VoltHome" onerror="this.outerHTML='VoltHome'"/></div>"""
            )
        } else {
            html.replace("{{watermark}}", "")
        }

        // Запас перед футером в потоке
        html = html.replace("{{footerSpacer}}", """<div class="footer-spacer"></div>""")

        return html
    }

    /** KPI (скрываем B/C для 1-ф; добавляем «Всего групп/Суммарный ток», если они присутствуют в модели). */
    private fun buildKpiBlock(meta: ReportMeta): String {
        val iA = formatA(meta.headlineCurrents["A"])
        val iB = formatA(meta.headlineCurrents["B"])
        val iC = formatA(meta.headlineCurrents["C"])
        val isSingle = meta.donut is DonutModel.IncomerLoad

        // Попробуем взять totalGroups/totalCurrentA, если вдруг есть
        val totalGroups = runCatching { meta.totalGroups }.getOrNull()
        val totalCurrentA = runCatching {
            meta.javaClass.getDeclaredField("totalCurrentA").apply { isAccessible = true }.get(meta) as? Double
        }.getOrNull()

        val incomerHuman = humanizeIncomer(
            runCatching { meta.incomerLabel }.getOrDefault("")
        )

        return buildString {
            append("""<div class="kpi">""")
            append("""<div>Дата: <b>${escape(meta.date)}</b></div>""")
            append("""<div>Вводной аппарат: <b>${escape(incomerHuman)}</b></div>""")
            if (totalGroups != null) append("""<div>Всего групп: <b>$totalGroups</b></div>""")
            if (totalCurrentA != null) append("""<div>Суммарный ток: <b>${df2.format(totalCurrentA)} $UNIT_A</b></div>""")
            append("""<div class="topline">""")
            append("""<span class="metric">Фаза A: <b>$iA</b></span>""")
            if (!isSingle) {
                if (iB.isNotBlank()) append("""<span class="metric">Фаза B: <b>$iB</b></span>""")
                if (iC.isNotBlank()) append("""<span class="metric">Фаза C: <b>$iC</b></span>""")
            }
            append("</div></div>")
        }
    }

    /** Легенда только для 3-ф. */
    private fun buildLegend(donut: DonutModel): String {
        if (donut !is DonutModel.PhaseDistribution) return ""
        val a = donut.valuesA[Phase.A] ?: 0.0
        val b = donut.valuesA[Phase.B] ?: 0.0
        val c = donut.valuesA[Phase.C] ?: 0.0
        val total = max(a + b + c, eps)
        val pa = a / total * 100.0
        val pb = b / total * 100.0
        val pc = 100.0 - pa - pb
        val maxVal = max(a, max(b, c))
        val em = 1e-3

        fun row(label: String, cls: String, amp: Double, pct: Double, bold: Boolean): String {
            val strongOpen = if (bold) "<span class=\"val\">" else ""
            val strongClose = if (bold) "</span>" else ""
            return """
              <div class="row">
                <div><span class="dot $cls"></span>$label</div>
                <div class="num">$strongOpen${df2.format(amp)} $UNIT_A$strongClose • ${df0.format(pct)}%</div>
              </div>
            """.trimIndent()
        }

        return """
          <div class="legend">
            ${row("Фаза A","a", a, pa, a >= maxVal - em)}
            ${row("Фаза B","b", b, pb, b >= maxVal - em)}
            ${row("Фаза C","c", c, pc, c >= maxVal - em)}
          </div>
        """.trimIndent()
    }

    /** Одна таблица на фазу: шапка один раз; каждая группа — отдельный tbody.group-block (без разрывов и двойных линий). */
    private fun buildPhasesTables(phases: List<ReportPhase>): String {
        if (phases.isEmpty()) return ""
        return buildString {
            phases.forEach { phase ->
                appendLine("""<div class="section">""")
                appendLine("""<h2 class="h2-tight phase-title">${escape(phase.name)}</h2>""")
                if (phase.groups.isEmpty()) {
                    appendLine("""<div class="group empty">—</div>""")
                } else {
                    appendLine("""<table class="phase-table">""")
                    appendLine(
                        """<thead><tr class="hdr"><th class="center">Устройство</th><th class="num">Мощность</th><th class="num">Ток</th></tr></thead>"""
                    )
                    // Каждая группа — отдельный tbody.group-block
                    phase.groups.forEach { g: ReportGroup ->
                        appendLine("""<tbody class="group-block">""")
                        // Заголовок группы (чип)
                        appendLine("""<tr class="group-row"><td class="center" colspan="3"><span class="chip">${escape(g.title)}</span></td></tr>""")
                        // Метаданные под чипом (аппарат/кабель), если есть
                        val metaLine = buildGroupMeta(g)
                        if (metaLine.isNotEmpty()) {
                            appendLine("""<tr class="group-meta"><td class="center" colspan="3">${escape(metaLine)}</td></tr>""")
                        }
                        // Устройства
                        g.devices.forEach { d ->
                            val (p, c) = pickDeviceNumbers(d)
                            appendLine(
                                """
                                <tr class="dev">
                                  <td class="center">${escape(d.name)}</td>
                                  <td class="num">${p ?: ""}</td>
                                  <td class="num">${c ?: ""}</td>
                                </tr>
                                """.trimIndent()
                            )
                        }
                        appendLine("</tbody>")
                    }
                    appendLine("""</table>""")
                }
                appendLine("""</div>""")
            }
        }
    }

    /** Собираем строку «Аппарат • Кабель» из алиасов. */
    private fun buildGroupMeta(g: ReportGroup): String {
        fun firstNonBlank(names: List<String>): String? {
            for (n in names) {
                val raw = runCatching {
                    val f = g.javaClass.getDeclaredField(n).apply { isAccessible = true }
                    f.get(g) as? String
                }.getOrNull()
                val v = raw?.trim()
                if (!v.isNullOrBlank()) return v
            }
            return null
        }
        val sw = firstNonBlank(groupSwitchAliases)
        val cable = firstNonBlank(groupCableAliases)
        return listOfNotNull(sw, cable).joinToString(" • ")
    }

    /**
     * Берём power/current из любых алиас-полей, иначе парсим legacy spec.
     * Если ток не найден, но есть мощность — считаем I = P/230 В.
     * Возвращаем отформатированные строки: Pair("2 200 Вт", "11,80 А").
     */
    private fun pickDeviceNumbers(d: ReportDevice): Pair<String?, String?> {
        // 1) читаем алиас-поля рефлексией
        fun getNumber(obj: Any, names: List<String>): Number? {
            for (n in names) {
                val num = runCatching {
                    val f = obj.javaClass.getDeclaredField(n).apply { isAccessible = true }
                    when (val v = f.get(obj)) {
                        is Int -> v
                        is Long -> v.toInt()
                        is Float -> v.toDouble()
                        is Double -> v
                        else -> null
                    }
                }.getOrNull()
                if (num != null) return num
            }
            return null
        }

        var pw: Double? = (getNumber(d, powerFieldAliases))?.toDouble()
        var ia: Double? = (getNumber(d, currentFieldAliases))?.toDouble()

        // 2) если что-то отсутствует — парсим spec
        if ((pw == null || ia == null) && d.spec.isNotBlank()) {
            val parsed = parseSpec(d.spec)
            if (pw == null) pw = parsed.first?.toDouble()
            if (ia == null) ia = parsed.second
        }

        // 3) если есть только мощность — считаем ток (упрощённо для рендера)
        if (ia == null && pw != null) ia = pw / VOLTAGE_DEFAULT

        val pStr = pw?.let { "${df0.format(it)} Вт" }
        val cStr = ia?.let { "${df2.format(it)} $UNIT_A" }
        return pStr to cStr
    }

    /** Парсинг строк вида "2.2 кВт, 11.8 А" / "2200 Вт, 11,8 А" (порядок свободный). */
    private fun parseSpec(spec: String): Pair<Int?, Double?> {
        // Нормализуем: запятая → точка, убираем неразрывные пробелы
        val s = spec.replace('\u00A0', ' ')
            .lowercase(Locale.getDefault())
            .replace(',', '.')
        val num = Regex("""\d+(?:\.\d+)?""")
        var pW: Int? = null
        var cA: Double? = null

        // мощность
        Regex("""${num.pattern}\s*(квт|kw|кw|вт|w)""").findAll(s).forEach { m ->
            val unitToken = m.groupValues[1]
            val v = num.find(m.value)?.value?.toDoubleOrNull() ?: return@forEach
            pW = if (unitToken.contains("к") || unitToken.contains("kw")) {
                (v * 1000).roundToInt()
            } else {
                v.roundToInt()
            }
        }

        // ток
        Regex("""${num.pattern}\s*(a|а)""").findAll(s).forEach { m ->
            val v = num.find(m.value)?.value?.toDoubleOrNull()
            if (v != null) cA = v
        }

        return pW to cA
    }

    /**
     * Донаты — инлайн SVG.
     * 3-ф: дуги <path> без щелей. 1-ф: прогресс-кольцо.
     */
    private fun buildDonutSection(model: DonutModel): String {
        return when (model) {
            is DonutModel.PhaseDistribution -> {
                val a = model.valuesA[Phase.A] ?: 0.0
                val b = model.valuesA[Phase.B] ?: 0.0
                val c = model.valuesA[Phase.C] ?: 0.0
                val total = max(a + b + c, eps)
                val pa = a / total * 100.0
                val pb = b / total * 100.0
                val pc = 100.0 - pa - pb

                val cx = 21.0
                val cy = 21.0
                val r = 15.915
                var start = -90.0

                fun arcPath(startDeg: Double, sweepDeg: Double): String {
                    val sweep = sweepDeg.coerceAtLeast(0.0)
                    val large = if (sweep > 180.0) 1 else 0
                    val endDeg = startDeg + sweep
                    val sx = cx + r * cos(Math.toRadians(startDeg))
                    val sy = cy + r * sin(Math.toRadians(startDeg))
                    val ex = cx + r * cos(Math.toRadians(endDeg))
                    val ey = cy + r * sin(Math.toRadians(endDeg))
                    return "M ${fmtUS(sx)} ${fmtUS(sy)} A ${fmtUS(r)} ${fmtUS(r)} 0 $large 1 ${fmtUS(ex)} ${fmtUS(ey)}"
                }

                val pathA = arcPath(start, pa / 100.0 * 360.0).also { start += pa / 100.0 * 360.0 }
                val pathB = arcPath(start, pb / 100.0 * 360.0).also { start += pb / 100.0 * 360.0 }
                val pathC = arcPath(start, pc / 100.0 * 360.0)

                """
                <div style="display:flex;flex-direction:column;align-items:center;margin-top:6pt;">
                  <svg viewBox="0 0 42 42" width="210" height="210" role="img" aria-label="Баланс фаз">
                    <circle cx="21" cy="21" r="$r" fill="none" stroke="#eeeeee" stroke-width="5"/>
                    <path d="$pathA" fill="none" stroke="#f2cc66" stroke-width="5" stroke-linecap="butt"/>
                    <path d="$pathB" fill="none" stroke="#5bbf72" stroke-width="5" stroke-linecap="butt"/>
                    <path d="$pathC" fill="none" stroke="#f26d6d" stroke-width="5" stroke-linecap="butt"/>
                  </svg>
                  <div class="donut-caption">Баланс фаз</div>
                </div>
                """.trimIndent()
            }
            is DonutModel.IncomerLoad -> {
                val used = max(model.usedA, 0.0)
                val limit = max(model.limitA, eps)
                val pct = (used / limit * 100.0).coerceIn(0.0, 100.0)
                val reserve = max(limit - used, 0.0)

                """
                <div style="position:relative;width:240px;height:240px;margin:8pt auto 0;">
                  <svg viewBox="0 0 42 42" width="240" height="240" role="img" aria-label="Загрузка вводного автомата">
                    <circle cx="21" cy="21" r="15.915" fill="none" stroke="#eeeeee" stroke-width="7"/>
                    <circle cx="21" cy="21" r="15.915" fill="none"
                      stroke="#7f4b57" stroke-width="7" stroke-linecap="butt"
                      stroke-dasharray="${fmtUS(pct)} ${fmtUS(100.0 - pct)}" stroke-dashoffset="25"/>
                  </svg>
                  <div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;gap:4px;">
                    <div style="font-weight:700;font-size:13px;">${df2.format(used)} $UNIT_A из ${df0.format(limit)} $UNIT_A</div>
                    <div style="font-size:12px;color:#666;">${df0.format(pct)}% • Запас: ${df2.format(reserve)} $UNIT_A</div>
                  </div>
                </div>
                <div class="donut-caption">Загрузка вводного автомата</div>
                <div class="donut-sub muted">Всего: ${df2.format(used)} $UNIT_A из ${df0.format(limit)} $UNIT_A • ${df0.format(pct)}% • Запас ${df2.format(reserve)} $UNIT_A</div>
                """.trimIndent()
            }
        }
    }

    private fun humanizeIncomer(raw: String): String {
        // Примеры: "MCB_PLUS_RCD, 4P, 25A C, Icn 6000, RCD A 300mA"
        val parts = raw.split(',', '·', '•').map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()

        parts.forEach { p0 ->
            val p = p0.trim()
            when {
                p.contains("MCB_PLUS_RCD", true) -> out += "Автомат + УЗО"
                p.matches(Regex("""\dP""", RegexOption.IGNORE_CASE)) -> {
                    val poles = p.lowercase().removeSuffix("p").toIntOrNull()
                    if (poles != null) out += "$poles полюса"
                }
                Regex("""(\d+)\s*A\s*([ABCD])?""", RegexOption.IGNORE_CASE).find(p) != null -> {
                    val m = Regex("""(\d+)\s*A\s*([ABCD])?""", RegexOption.IGNORE_CASE).find(p)
                    val a = m?.groupValues?.getOrNull(1)
                    val ch = m?.groupValues?.getOrNull(2)?.uppercase()
                    out += buildString {
                        append("${a ?: ""} A")
                        if (!ch.isNullOrBlank()) append(", характеристика $ch")
                    }
                }
                p.contains("ICN", true) -> {
                    val n = Regex("""\d+""").find(p)?.value
                    if (n != null) out += "отключающая способность ${df0.format(n.toInt())} $UNIT_A"
                }
                p.contains("RCD", true) -> {
                    val type = Regex("""RCD\s*([ABCD])""", RegexOption.IGNORE_CASE).find(p)?.groupValues?.getOrNull(1)?.uppercase()
                    val sens = Regex("""(\d+)\s*mA""", RegexOption.IGNORE_CASE).find(p)?.groupValues?.getOrNull(1)
                    val text = buildString {
                        append("тип УЗО")
                        if (!type.isNullOrBlank()) append(" $type")
                        if (!sens.isNullOrBlank()) append(", чувствительность ${df0.format(sens.toInt())} мА")
                    }
                    out += text
                }
                else -> out += p // оставим как есть (линейка/производитель)
            }
        }
        return out.joinToString(" • ").ifBlank { raw }
    }

    private fun loadTemplate(path: String): String {
        context.assets.open(path).use { ins ->
            BufferedReader(InputStreamReader(ins)).use { br ->
                return br.readText()
            }
        }
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // координаты/длины для SVG всегда в US-формате
    private fun fmtUS(v: Double): String = String.format(Locale.US, "%.6f", v)

    private fun formatA(v: Double?): String = when (v) {
        null -> ""
        else -> "${df2.format(v)} $UNIT_A"
    }

    // Fallback на случай отсутствия ассетов
    private val FALLBACK_TEMPLATE = """
        <!doctype html>
        <html lang="ru">
        <head><meta charset="utf-8"/><title>VoltHome — Экспликация</title>$INLINE_STYLE</head>
        <body style="padding-bottom:24mm;">
          <h2>VoltHome — Экспликация</h2>
          {{kpiBlock}}
          <div class="donut-wrap"><div class="donut-col">{{donutSection}}</div><div class="legend-col">{{legendSection}}</div></div>
          <hr/>
          <div>{{phasesHtml}}</div>
          {{watermark}}
          {{footerSpacer}}
        </body></html>
    """.trimIndent()
}