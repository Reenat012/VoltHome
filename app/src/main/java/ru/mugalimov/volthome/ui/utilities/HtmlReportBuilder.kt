package ru.mugalimov.volthome.ui.utilities

import android.content.Context
import androidx.annotation.WorkerThread
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.report.DonutModel
import ru.mugalimov.volthome.domain.model.report.ReportDevice
import ru.mugalimov.volthome.domain.model.report.ReportGroup
import ru.mugalimov.volthome.domain.model.report.ReportModel
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
    private val df2 = DecimalFormat("#,##0.00", ruSymbols)
    private val df1 = DecimalFormat("#,##0.0", ruSymbols)
    private val df0 = DecimalFormat("#,##0", ruSymbols)

    private val UNIT_A = "\u0410"
    private val VOLTAGE_DEFAULT = 230.0
    private val eps = 1e-6

    private val powerFieldAliases = listOf("powerW", "power", "watt", "pW")
    private val currentFieldAliases = listOf("currentA", "amp", "a")

    private val groupSwitchAliases = listOf(
        "switchLabel", "groupSwitchLabel", "apparatusLabel",
        "protectionLabel", "breakerLabel", "rcdLabel", "deviceLabel"
    )
    private val groupCableAliases =
        listOf("cableLabel", "lineLabel", "wireLabel", "cableInfo", "lineInfo")

    // Для гибкого чтения моделей steps/assumptions/warnings/normRefs без жёсткой зависимости
    private val titleAliases = listOf("title", "name", "label")
    private val messageAliases = listOf("message", "text", "description", "details", "hint")
    private val severityAliases = listOf("severity", "level", "type")

    // —— ТОЛЬКО CSS изменён: добавлены жёсткие запреты разрыва и префиксы ——
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

          table.phase-table { width: 100%; border-collapse: collapse; margin: 0 0 12px 0; }
          table.phase-table th, table.phase-table td { padding: 10px 0; }
          table.phase-table th.center, table.phase-table td.center { text-align: left; }
          table.phase-table th.num, table.phase-table td.num { text-align: right; white-space: nowrap; }

          thead.phase-head th { border-bottom: 1px solid #E5E7EB; }
          thead.phase-head { display: table-header-group; }

          table.phase-table tbody tr.dev td { border-bottom: 1px solid #E5E7EB; }
          table.phase-table tbody tr.dev:last-child td { border-bottom: 0; }

          tr.group-start td {
            border-top: 2px solid #CBD5E1;
            border-bottom: 0;
            background: #F9FAFB;
          }
          .chip {
            display:inline-block; padding:4px 10px; border-radius:9999px;
            background:#F3F4F6; margin-right:10px; font-weight:600;
          }
          .meta-inline { display:inline-block; font-size:12px; color:#6B7280; vertical-align:middle; }

          /* ————— Антиорфаны: запираем разрывы везде, где это возможно ————— */
          tbody.group-block {
            break-inside: avoid !important;
            page-break-inside: avoid !important;
            -webkit-column-break-inside: avoid !important;
            -webkit-region-break-inside: avoid !important;
          }
          tr.group-start,
          tr.dev {
            break-inside: avoid !important;
            page-break-inside: avoid !important;
            -webkit-column-break-inside: avoid !important;
            -webkit-region-break-inside: avoid !important;
          }
          /* сцепляем чип с первой строкой устройств */
          tr.group-start { page-break-after: avoid !important; }

          .h2-tight { margin: 0 0 8px 0; }
          
          /* ————— Новые секции: расчёт/предупреждения/нормы ————— */
                    .explain { margin: 14px 0 18px 0; }
                    .explain .block {
                      border: 1px solid #E5E7EB;
                      border-radius: 12px;
                      padding: 12px 12px;
                      margin: 10px 0;
                      background: #FFFFFF;
                      break-inside: avoid !important;
                      page-break-inside: avoid !important;
                    }
                    .explain .block h3 { margin: 0 0 8px 0; font-size: 13px; font-weight: 700; }
                    .explain .muted { color: #6B7280; font-size: 12px; }
                    .explain ul { margin: 6px 0 0 18px; padding: 0; }
                    .explain li { margin: 4px 0; }
          
                    .step-title {
                      display:flex;
                      align-items:baseline;
                      gap:8px;
                      font-weight:700;
                      font-size:12.5px;
                      margin: 0 0 6px 0;
                    }
                    .pill {
                      display:inline-block;
                      padding: 2px 8px;
                      border-radius: 9999px;
                      font-size: 11px;
                      font-weight: 700;
                      background: #F3F4F6;
                      color: #111827;
                    }
                    .pill.warn { background:#FEF3C7; color:#92400E; }
                    .pill.err  { background:#FEE2E2; color:#991B1B; }
                    .pill.info { background:#E5E7EB; color:#374151; }
          
                    .warn-item { border-left: 4px solid #D1D5DB; padding-left: 10px; margin: 10px 0; }
                    .warn-item.warn { border-left-color: #F59E0B; }
                    .warn-item.err { border-left-color: #EF4444; }
                    .warn-item .t { font-weight:700; }
                    .warn-item .m { margin-top: 2px; color:#374151; font-size:12px; }
          
                    .norm-list .code { font-weight: 800; }
                    .norm-list .note { color:#6B7280; font-size:12px; }
        </style>
    """.trimIndent()

    @WorkerThread
    fun build(
        model: ReportModel,
        includeProfessionalSections: Boolean = false
    ): String {
        val htmlTemplate = runCatching { loadTemplate("report_pdf/template.html") }
            .getOrElse { FALLBACK_TEMPLATE }

        var html = if (htmlTemplate.contains("</head>", ignoreCase = true)) {
            htmlTemplate.replace(Regex("</head>", RegexOption.IGNORE_CASE), "$INLINE_STYLE</head>")
        } else {
            "$INLINE_STYLE$htmlTemplate"
        }

        html = html.replace("{{projectName}}", escape(model.header.projectName))
            .replace("{{date}}", escape(model.header.date))
            .replace("{{kpiBlock}}", buildKpiBlock(model))
            .replace("{{donutSection}}", buildDonutSection(model.donut))
            .replace("{{legendSection}}", buildLegend(model.donut))

        val explainHtml = if (includeProfessionalSections) buildExplainSections(model) else ""
        val phasesHtml = buildPhasesTables(model.phases)

        html = if (html.contains("{{explainHtml}}")) {
            html.replace("{{explainHtml}}", explainHtml)
                .replace("{{phasesHtml}}", phasesHtml)
                .replace("{{phases}}", phasesHtml)
        } else {
            val combined = explainHtml + phasesHtml
            html.replace("{{phasesHtml}}", combined)
                .replace("{{phases}}", combined)
        }

        html = if (!includeProfessionalSections) {
            html.replace(
                "{{watermark}}",
                """<div class="watermark"><img src="img/logo.png" alt="VoltHome" onerror="this.outerHTML='VoltHome'"/></div>"""
            )
        } else html.replace("{{watermark}}", "")

        html = html.replace("{{footerSpacer}}", """<div class="footer-spacer"></div>""")
        return html
    }

    private fun buildExplainSections(model: ReportModel): String {
        // PRO-путь: строго берём из новой модели
        val pro = model.professional ?: run {
            // Legacy fallback: если professional ещё не прокинут, оставляем старое поведение
            // (не ломаем существующий экспорт до полной миграции)
            return buildExplainSectionsLegacy(model)
        }

        val hasEvidence = pro.evidence.isNotEmpty()
        val hasWarnings = pro.warnings.isNotEmpty()
        val hasNorms = pro.normRefs.isNotEmpty() // по DoD секция должна быть всегда в PRO

        if (!hasEvidence && !hasWarnings && !hasNorms) return ""

        return buildString {
            appendLine("""<div class="explain">""")

            // 1) Обоснования / Evidence
            if (hasEvidence) {
                appendLine("""<div class="block">""")
                appendLine("""<h3>Обоснования</h3>""")
                appendLine("<ol>")
                pro.evidence.forEach { e ->
                    appendLine(
                        """
                    <li>
                      <div class="t"><b>${escape(e.title)}</b></div>
                      <div class="m">${escape(e.body)}</div>
                    </li>
                    """.trimIndent()
                    )
                }
                appendLine("</ol>")
                appendLine("""</div>""")
            }

            // 2) Предупреждения
            if (hasWarnings) {
                appendLine("""<div class="block">""")
                appendLine("""<h3>Предупреждения</h3>""")
                pro.warnings.forEach { w ->
                    appendLine(formatProWarning(w))
                }
                appendLine("""</div>""")
            }

            // 3) Нормативные ссылки (в PRO секция должна присутствовать всегда)
            appendLine("""<div class="block">""")
            appendLine("""<h3>Нормативные ссылки</h3>""")
            appendLine("""<div class="norm-list">""")
            appendLine("<ul>")
            if (pro.normRefs.isEmpty()) {
                appendLine("""<li><span class="code">Справочно</span><div class="note">Раздел доступен в PRO.</div></li>""")
            } else {
                pro.normRefs.forEach { n ->
                    val head = buildString {
                        append("""<span class="code">${escape(n.source)}</span>""")
                        val section = n.section?.trim().takeIf { !it.isNullOrBlank() }
                        if (section != null) append(""" — ${escape(section)}""")
                    }
                    val note = n.note?.trim().takeIf { !it.isNullOrBlank() }
                        ?.let { """<div class="note">${escape(it)}</div>""" }
                        ?: ""
                    appendLine("""<li>$head$note</li>""")
                }
            }
            appendLine("</ul>")
            appendLine("""</div>""")
            appendLine("""</div>""")

            appendLine("""</div>""")
        }
    }

    private fun buildExplainSectionsLegacy(model: ReportModel): String {
        val hasSteps = model.steps.isNotEmpty()
        val hasAssumptions = model.assumptions.isNotEmpty()
        val hasWarnings = model.warnings.isNotEmpty()
        val hasNorms = model.normRefs.isNotEmpty()

        if (!hasSteps && !hasAssumptions && !hasWarnings && !hasNorms) return ""

        return buildString {
            appendLine("""<div class="explain">""")

            // 1) Расчёт
            if (hasSteps) {
                appendLine("""<div class="block">""")
                appendLine("""<h3>Расчёт</h3>""")
                model.steps.forEachIndexed { idx, step ->
                    appendLine("""<div class="step">""")
                    appendLine(
                        """
                    <div class="step-title">
                      <span class="pill info">${idx + 1}</span>
                      <span>${escape(step.title)}</span>
                    </div>
                    """.trimIndent()
                    )
                    if (step.lines.isNotEmpty()) {
                        appendLine("<ul>")
                        step.lines.forEach { line -> appendLine("""<li>${escape(line)}</li>""") }
                        appendLine("</ul>")
                    } else {
                        appendLine("""<div class="muted">—</div>""")
                    }
                    appendLine("</div>")
                }
                appendLine("""</div>""")
            }

            // 2) Допущения
            if (hasAssumptions) {
                appendLine("""<div class="block">""")
                appendLine("""<h3>Допущения</h3>""")
                appendLine("<ul>")
                model.assumptions.forEach { a ->
                    appendLine("""<li>${formatAssumption(a)}</li>""")
                }
                appendLine("</ul>")
                appendLine("""</div>""")
            }

            // 3) Предупреждения
            if (hasWarnings) {
                appendLine("""<div class="block">""")
                appendLine("""<h3>Предупреждения</h3>""")
                model.warnings.forEach { w ->
                    appendLine(formatWarning(w))
                }
                appendLine("""</div>""")
            }

            // 4) Нормы
            if (hasNorms) {
                appendLine("""<div class="block">""")
                appendLine("""<h3>Нормативные ссылки</h3>""")
                appendLine("""<div class="norm-list">""")
                appendLine("<ul>")
                model.normRefs.forEach { n ->
                    val head = buildString {
                        append("""<span class="code">${escape(n.code)}</span>""")
                        if (n.title.isNotBlank()) append(""" — ${escape(n.title)}""")
                    }
                    val note = n.note.takeIf { it.isNotBlank() }
                        ?.let { """<div class="note">${escape(it)}</div>""" }
                        ?: ""
                    appendLine("""<li>$head$note</li>""")
                }
                appendLine("</ul>")
                appendLine("""</div>""")
                appendLine("""</div>""")
            }

            appendLine("""</div>""")
        }
    }

    private fun formatProWarning(w: ru.mugalimov.volthome.domain.model.report.professional.ReportWarningItem): String {
        val cls = when (w.severity) {
            ru.mugalimov.volthome.domain.model.report.professional.ReportWarningItem.Severity.CRITICAL -> "err"
            ru.mugalimov.volthome.domain.model.report.professional.ReportWarningItem.Severity.WARNING -> "warn"
            ru.mugalimov.volthome.domain.model.report.professional.ReportWarningItem.Severity.INFO -> "info"
        }

        val pill = when (cls) {
            "err" -> """<span class="pill err">ОШИБКА</span>"""
            "warn" -> """<span class="pill warn">ВНИМАНИЕ</span>"""
            else -> """<span class="pill info">ИНФО</span>"""
        }

        val itemCls = when (cls) {
            "err" -> "warn-item err"
            "warn" -> "warn-item warn"
            else -> "warn-item"
        }

        return buildString {
            appendLine("""<div class="$itemCls">""")
            appendLine("""<div class="t">$pill ${escape(w.title)}</div>""")
            appendLine("""<div class="m">${escape(w.message)}</div>""")
            val scope = w.scope?.takeIf { it.isNotBlank() }
            if (scope != null) {
                appendLine("""<div class="muted">${escape(scope)}</div>""")
            }
            appendLine("""</div>""")
        }
    }


    private fun formatAssumption(a: Any): String {
        val title = readStringField(a, titleAliases)?.takeIf { it.isNotBlank() }
        val msg = readStringField(a, messageAliases)?.takeIf { it.isNotBlank() }
        return when {
            title != null && msg != null -> "<b>${escape(title)}</b>: ${escape(msg)}"
            title != null -> "<b>${escape(title)}</b>"
            msg != null -> escape(msg)
            else -> escape(a.toString())
        }
    }

    private fun formatWarning(w: Any): String {
        val severityRaw = readAnyField(w, severityAliases)
            ?.toString()
            ?.lowercase(Locale.getDefault())
            .orEmpty()

        val cls = when {
            severityRaw.contains("error") || severityRaw.contains("critical") -> "err"
            severityRaw.contains("warn") -> "warn"
            else -> "info"
        }

        val pill = when (cls) {
            "err" -> """<span class="pill err">ОШИБКА</span>"""
            "warn" -> """<span class="pill warn">ВНИМАНИЕ</span>"""
            else -> """<span class="pill info">ИНФО</span>"""
        }

        val title = readStringField(w, titleAliases)?.takeIf { it.isNotBlank() } ?: "Предупреждение"
        val msg = readStringField(w, messageAliases)?.takeIf { it.isNotBlank() }

        val itemCls = when (cls) {
            "err" -> "warn-item err"
            "warn" -> "warn-item warn"
            else -> "warn-item"
        }

        return buildString {
            appendLine("""<div class="$itemCls">""")
            appendLine("""<div class="t">$pill ${escape(title)}</div>""")
            if (msg != null) appendLine("""<div class="m">${escape(msg)}</div>""")
            appendLine("""</div>""")
        }
    }

    private fun readStringField(obj: Any, names: List<String>): String? {
        for (n in names) {
            val v = runCatching {
                val f = obj.javaClass.getDeclaredField(n).apply { isAccessible = true }
                f.get(obj)
            }.getOrNull()

            when (v) {
                is String -> return v
                null -> Unit
                else -> {
                    val s = v.toString()
                    if (s.isNotBlank()) return s
                }
            }
        }
        return null
    }

    private fun readAnyField(obj: Any, names: List<String>): Any? {
        for (n in names) {
            val v = runCatching {
                val f = obj.javaClass.getDeclaredField(n).apply { isAccessible = true }
                f.get(obj)
            }.getOrNull()
            if (v != null) return v
        }
        return null
    }

    private fun buildKpiBlock(model: ReportModel): String {
        val currents = model.kpis.headlineCurrents

        val iA = formatA(currents["A"])
        val iB = formatA(currents["B"])
        val iC = formatA(currents["C"])

        val isSingle = model.donut is DonutModel.IncomerLoad

        val incomerHuman = humanizeIncomer(model.header.incomerLabel)

        return buildString {
            append("""<div class="kpi">""")
            append("""<div>Дата: <b>${escape(model.header.date)}</b></div>""")
            append("""<div>Вводной аппарат: <b>${escape(incomerHuman)}</b></div>""")

            model.kpis.totalGroups?.let { tg ->
                append("""<div>Всего групп: <b>$tg</b></div>""")
            }
            model.kpis.totalCurrentA?.let { totalI ->
                append("""<div>Суммарный ток: <b>${df2.format(totalI)} $UNIT_A</b></div>""")
            }

            append("""<div class="topline">""")
            append("""<span class="metric">Фаза A: <b>$iA</b></span>""")
            if (!isSingle) {
                if (iB.isNotBlank()) append("""<span class="metric">Фаза B: <b>$iB</b></span>""")
                if (iC.isNotBlank()) append("""<span class="metric">Фаза C: <b>$iC</b></span>""")
            }
            append("</div></div>")
        }
    }

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
                <div class="num">$strongOpen${df2.format(amp)} $UNIT_A$strongClose • ${
                df0.format(
                    pct
                )
            }%</div>
              </div>
            """.trimIndent()
        }

        return """
          <div class="legend">
            ${row("Фаза A", "a", a, pa, a >= maxVal - em)}
            ${row("Фаза B", "b", b, pb, b >= maxVal - em)}
            ${row("Фаза C", "c", c, pc, c >= maxVal - em)}
          </div>
        """.trimIndent()
    }

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
                        appendLine(
                            """
                            <tr class="group-start">
                              <td class="center" colspan="3">
                                <span class="chip">${escape(g.title)}</span>
                                ${
                                if (metaLine.isNotEmpty()) """<span class="meta-inline">${
                                    escape(
                                        metaLine
                                    )
                                }</span>""" else ""
                            }
                              </td>
                            </tr>
                            """.trimIndent()
                        )
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
                    return "M ${fmtUS(sx)} ${fmtUS(sy)} A ${fmtUS(r)} ${fmtUS(r)} 0 $large 1 ${
                        fmtUS(
                            ex
                        )
                    } ${fmtUS(ey)}"
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
                    <div style="font-weight:700;font-size:13px;">${df2.format(used)} $UNIT_A из ${
                    df0.format(
                        limit
                    )
                } $UNIT_A</div>
                    <div style="font-size:12px;color:#666;">${df0.format(pct)}% • Запас: ${
                    df2.format(
                        reserve
                    )
                } $UNIT_A</div>
                  </div>
                </div>
                <div class="donut-caption">Загрузка вводного автомата</div>
                <div class="donut-sub muted">Всего: ${df2.format(used)} $UNIT_A из ${
                    df0.format(
                        limit
                    )
                } $UNIT_A • ${df0.format(pct)}% • Запас ${df2.format(reserve)} $UNIT_A</div>
                """.trimIndent()
            }
        }
    }

    private fun humanizeIncomer(raw0: String): String {
        if (raw0.isBlank()) return ""
        val parts = raw0.replace('•', ',').replace('·', ',')
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }

        val out = mutableListOf<String>()
        for (p in parts) {
            val lower = p.lowercase(Locale.getDefault())
            if (p.equals("MCB_ONLY", true) || lower.contains("mcb only")) {
                out += "Автомат"; continue
            } else if (p.equals(
                    "MCB_PLUS_RCD",
                    true
                ) || lower.contains("mcb+rcd") || lower.contains("mcb_plus_rcd")
            ) {
                out += "Автомат + УЗО"; continue
            } else if (p.equals("RCBO", true) || lower.contains("rcbo")) {
                out += "Диффавтомат"; continue
            }

            val polesMatch =
                Regex("""^\s*(\d+)\s*(p|pol(e|es))?\s*$""", RegexOption.IGNORE_CASE).matchEntire(p)
            if (polesMatch != null) {
                val n = polesMatch.groupValues[1].toIntOrNull()
                if (n != null) out += "$n ${if (n == 1) "полюс" else "полюса"}" else out += p
                continue
            }

            val nomMatch = Regex("""(\d+)\s*A\s*,?\s*([ABCD])?""", RegexOption.IGNORE_CASE).find(p)
            if (nomMatch != null) {
                val a = nomMatch.groupValues.getOrNull(1)
                val curve = nomMatch.groupValues.getOrNull(2)?.uppercase()
                out += buildString {
                    append("${a} А")
                    if (!curve.isNullOrBlank()) append(", характеристика $curve")
                }
                continue
            }

            if (lower.contains("icn") || lower.contains("ka") || lower.contains("ка")) {
                val ka = Regex("""(\d+(?:[\.,]\d+)?)\s*k?a""", RegexOption.IGNORE_CASE)
                    .find(p)?.groupValues?.getOrNull(1)
                val onlyNum = Regex("""\d+""").find(p)?.value
                val amps = if (ka != null) (ka.replace(',', '.').toDoubleOrNull()
                    ?: 0.0) * 1000.0 else onlyNum?.toDoubleOrNull()
                if (amps != null) {
                    out += "отключающая способность ${df0.format(amps)} $UNIT_A"; continue
                }
            }

            if (lower.contains("rcd")) {
                val type = Regex(
                    """rcd\s*([A-Z]+)""",
                    RegexOption.IGNORE_CASE
                ).find(p)?.groupValues?.getOrNull(1)?.uppercase()
                val sens = Regex(
                    """(\d+)\s*mA""",
                    RegexOption.IGNORE_CASE
                ).find(p)?.groupValues?.getOrNull(1)
                out += buildString {
                    append("тип УЗО")
                    if (!type.isNullOrBlank()) append(" $type")
                    if (!sens.isNullOrBlank()) append(", чувствительность ${df0.format(sens.toInt())} мА")
                }
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

    private fun fmtUS(v: Double): String = String.format(Locale.US, "%.6f", v)

    private fun formatA(v: Double?): String =
        when (v) {
            null -> ""
            else -> "${df2.format(v)} $UNIT_A"
        }

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