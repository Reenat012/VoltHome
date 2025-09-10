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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

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

    // Вставляемый CSS: одна таблица на фазу, повтор шапки при печати, выделение групп
    private val INLINE_STYLE = """
        <style>
          body { padding-bottom: 96px; }

          .footer {
            margin-top: 24px;
            padding-top: 8px;
            border-top: 1px solid #E5E7EB;
            font-size: 11px;
            color: #6B7280;
          }
          .footer-spacer { height: 72px; }

          /* Таблица фазы: одна на фазу */
          table.phase-table { width: 100%; border-collapse: collapse; margin: 0 0 12px 0; }
          table.phase-table th, table.phase-table td { padding: 10px 0; }
          table.phase-table th.center, table.phase-table td.center { text-align: left; }
          table.phase-table th.num, table.phase-table td.num { text-align: right; white-space: nowrap; }

          thead.phase-head th { border-bottom: 1px solid #E5E7EB; }
          /* ПОВТОР ШАПКИ НА КАЖДОЙ СТРАНИЦЕ */
          thead.phase-head { display: table-header-group; }

          /* Разделители строк устройств */
          table.phase-table tbody tr.dev td { border-bottom: 1px solid #E5E7EB; }
          table.phase-table tbody tr.dev:last-child td { border-bottom: 0; }

          /* Строка начала группы (чип + мета в одну строку), визуальное выделение */
          tr.group-start td {
            border-top: 2px solid #CBD5E1; /* толще, чтобы группа читалась */
            border-bottom: 0;
            background: #F9FAFB;          /* светлый фон для отделения от устройств */
          }
          .chip {
            display:inline-block;
            padding:4px 10px;
            border-radius:9999px;
            background:#F3F4F6;
            margin-right:10px;
            font-weight:600;
          }
          .meta-inline {
            display:inline-block;
            font-size:12px;
            color:#6B7280;
            vertical-align:middle;
          }

          /* Неразрывность: чип+мета+первое устройство едут вместе */
          tbody.group-block { break-inside: avoid; page-break-inside: avoid; }
          tr.group-start, thead.phase-head { break-inside: avoid; page-break-inside: avoid; }

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

    /** KPI: приводим вводной аппарат к человекочитаемому виду. */
    private fun buildKpiBlock(meta: ReportMeta): String {
        val iA = formatA(meta.headlineCurrents["A"])
        val iB = formatA(meta.headlineCurrents["B"])
        val iC = formatA(meta.headlineCurrents["C"])
        val isSingle = meta.donut is DonutModel.IncomerLoad

        val totalGroups = runCatching { meta.totalGroups }.getOrNull()
        val totalCurrentA = runCatching {
            meta.javaClass.getDeclaredField("totalCurrentA").apply { isAccessible = true }.get(meta) as? Double
        }.getOrNull()

        val incomerHuman = humanizeIncomer(meta.incomerLabel ?: "")

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

    /**
     * ОДНА таблица на фазу.
     * Для каждой группы — отдельный <tbody class="group-block">:
     *   - первая строка tbody: чип + мета (в одной ячейке)
     *   - далее строки устройств
     */
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
                    appendLine("""<thead class="phase-head"><tr><th class="center">Устройство</th><th class="num">Мощность</th><th class="num">Ток</th></tr></thead>""")
                    phase.groups.forEach { g: ReportGroup ->
                        val metaLine = buildGroupMeta(g)
                        appendLine("""<tbody class="group-block">""")
                        // Старт группы: чип + мета — в одной строке/ячейке
                        appendLine(
                            """
                            <tr class="group-start">
                              <td class="center" colspan="3">
                                <span class="chip">${escape(g.title)}</span>
                                ${if (metaLine.isNotEmpty()) """<span class="meta-inline">${escape(metaLine)}</span>""" else ""}
                              </td>
                            </tr>
                            """.trimIndent()
                        )
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
                    appendLine("</table>")
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
     * Если ток не найден, но есть мощность — считаем I = P/230 В (для рендера).
     * Возвращаем отформатированные строки.
     */
    private fun pickDeviceNumbers(d: ReportDevice): Pair<String?, String?> {
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

        if ((pw == null || ia == null) && d.spec.isNotBlank()) {
            val parsed = parseSpec(d.spec)
            if (pw == null) pw = parsed.first?.toDouble()
            if (ia == null) ia = parsed.second
        }

        if (ia == null && pw != null) ia = pw / VOLTAGE_DEFAULT

        val pStr = pw?.let { "${df0.format(it)} Вт" }
        val cStr = ia?.let { "${df2.format(it)} $UNIT_A" }
        return pStr to cStr
    }

    /** Парсинг строк вида "2.2 кВт, 11.8 А" / "2200 Вт, 11,8 А". */
    private fun parseSpec(spec: String): Pair<Int?, Double?> {
        val s = spec.replace('\u00A0', ' ')
            .lowercase(Locale.getDefault())
            .replace(',', '.')
        val num = Regex("""\d+(?:\.\d+)?""")
        var pW: Int? = null
        var cA: Double? = null

        Regex("""${num.pattern}\s*(квт|kw|кw|вт|w)""").findAll(s).forEach { m ->
            val unitToken = m.groupValues[1]
            val v = num.find(m.value)?.value?.toDoubleOrNull() ?: return@forEach
            pW = if (unitToken.contains("к") || unitToken.contains("kw")) {
                (v * 1000).roundToInt()
            } else {
                v.roundToInt()
            }
        }

        Regex("""${num.pattern}\s*(a|а)""").findAll(s).forEach { m ->
            val v = num.find(m.value)?.value?.toDoubleOrNull()
            if (v != null) cA = v
        }

        return pW to cA
    }

    /**
     * Донаты — инлайн SVG.
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

    /** Переводит «сырой» incomerLabel в человеческий русский. */
    private fun humanizeIncomer(raw0: String): String {
        if (raw0.isBlank()) return ""
        val parts = raw0
            .replace('•', ',')
            .replace('·', ',')
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val out = mutableListOf<String>()
        for (p in parts) {
            val lower = p.lowercase(Locale.getDefault())

            if (p.equals("MCB_ONLY", true) || lower.contains("mcb only")) {
                out += "Автомат"; continue
            } else if (p.equals("MCB_PLUS_RCD", true) || lower.contains("mcb+rcd") || lower.contains("mcb_plus_rcd")) {
                out += "Автомат + УЗО"; continue
            } else if (p.equals("RCBO", true) || lower.contains("rcbo")) {
                out += "Диффавтомат"; continue
            }

            // Полюса: 1P / 2P / 3P / 4P / "4 poles"
            val polesMatch = Regex("""^\s*(\d+)\s*(p|pol(e|es))?\s*$""", RegexOption.IGNORE_CASE).matchEntire(p)
            if (polesMatch != null) {
                val n = polesMatch.groupValues[1].toIntOrNull()
                if (n != null) out += "$n ${if (n == 1) "полюс" else "полюса"}" else out += p
                continue
            }

            // Номинал и кривая: "16A C" или "16 A, C"
            val nomMatch = Regex("""(\d+)\s*A\s*,?\s*([ABCD])?""", RegexOption.IGNORE_CASE).find(p)
            if (nomMatch != null) {
                val a = nomMatch.groupValues.getOrNull(1)
                val curve = nomMatch.groupValues.getOrNull(2)?.uppercase()
                val sb = StringBuilder()
                sb.append("${a} А")
                if (!curve.isNullOrBlank()) sb.append(", характеристика $curve")
                out += sb.toString()
                continue
            }

            // Icn 6000 / 10kA
            if (lower.contains("icn") || lower.contains("ka") || lower.contains("ка")) {
                val ka = Regex("""(\d+(?:[\.,]\d+)?)\s*k?a""", RegexOption.IGNORE_CASE).find(p)?.groupValues?.getOrNull(1)
                val onlyNum = Regex("""\d+""").find(p)?.value
                val amps = if (ka != null) (ka.replace(',', '.').toDoubleOrNull() ?: 0.0) * 1000.0 else onlyNum?.toDoubleOrNull()
                if (amps != null) { out += "отключающая способность ${df0.format(amps)} $UNIT_A"; continue }
            }

            // RCD A 30mA / RCD AC 100 mA
            if (lower.contains("rcd")) {
                val type = Regex("""rcd\s*([A-Z]+)""", RegexOption.IGNORE_CASE).find(p)?.groupValues?.getOrNull(1)?.uppercase()
                val sens = Regex("""(\d+)\s*mA""", RegexOption.IGNORE_CASE).find(p)?.groupValues?.getOrNull(1)
                val text = buildString {
                    append("тип УЗО")
                    if (!type.isNullOrBlank()) append(" $type")
                    if (!sens.isNullOrBlank()) append(", чувствительность ${df0.format(sens.toInt())} мА")
                }
                out += text
                continue
            }

            out += p
        }

        return out.joinToString(" • ").ifBlank { raw0 }
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