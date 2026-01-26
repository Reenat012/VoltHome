package ru.mugalimov.volthome.ui.utilities

import android.content.Context
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.report.DonutModel
import ru.mugalimov.volthome.domain.model.report.ReportDevice
import ru.mugalimov.volthome.domain.model.report.ReportGroup
import ru.mugalimov.volthome.domain.model.report.ReportModel
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import ru.mugalimov.volthome.domain.report.InlineNormatives
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

    private val df2 = DecimalFormat("#,##0.00", ruSymbols)
    private val df1 = DecimalFormat("#,##0.0", ruSymbols)
    private val df0 = DecimalFormat("#,##0", ruSymbols)

    private val UNIT_A = "\u0410"
    private val VOLTAGE_DEFAULT = 230.0
    private val eps = 1e-6

    // Legacy/fallback: если ReportDevice в какой-то ветке приходит без полей powerW/currentA,
    // пытаемся вытащить числа из spec (но это *не* основной путь).
    private val powerFieldAliases = listOf("powerW", "power", "watt", "pW")
    private val currentFieldAliases = listOf("currentA", "amp", "a")

    private val groupSwitchAliases = listOf(
        "switchLabel", "groupSwitchLabel", "apparatusLabel",
        "protectionLabel", "breakerLabel", "rcdLabel", "deviceLabel"
    )
    private val groupCableAliases =
        listOf("cableLabel", "lineLabel", "wireLabel", "cableInfo", "lineInfo")

    /**
     * ✅ “По-взрослому”:
     * 1) Template.html — единственный источник основной вёрстки и CSS.
     * 2) В рантайме мы инжектим ТОЛЬКО маленький “runtime override”:
     *    - выставляем классы профиля на <body> (vh-free / vh-pro)
     *    - жёстко гейтим антиорфаны: PRO = avoid, FREE = auto
     *    - принудительно прибиваем выравнивание к левому краю (donut-wrap + text-align)
     * 3) Большой FALLBACK_STYLE оставляем только для FALLBACK_TEMPLATE (если шаблон не загрузился).
     *
     * Это убирает дрейф стилей и гарантирует, что FREE не уезжает на следующую страницу
     * из-за глобального break-inside: avoid.
     */
    private val RUNTIME_OVERRIDES_STYLE = """
        <style id="vh-runtime-overrides">
          /* Профиль навешиваем на body: vh-free / vh-pro */
          body.vh-free tbody.group-block,
          body.vh-free tr.group-start,
          body.vh-free tr.dev {
            break-inside: auto !important;
            page-break-inside: auto !important;
            -webkit-column-break-inside: auto !important;
            -webkit-region-break-inside: auto !important;
          }
          body.vh-free tr.group-start { page-break-after: auto !important; }

          /* Антиорфаны разрешены ТОЛЬКО в PRO */
          body.vh-pro tbody.group-block {
            break-inside: avoid !important;
            page-break-inside: avoid !important;
            -webkit-column-break-inside: avoid !important;
            -webkit-region-break-inside: avoid !important;
          }
          body.vh-pro tr.group-start,
          body.vh-pro tr.dev {
            break-inside: avoid !important;
            page-break-inside: avoid !important;
            -webkit-column-break-inside: avoid !important;
            -webkit-region-break-inside: avoid !important;
          }
          body.vh-pro tr.group-start { page-break-after: avoid !important; }

                    /* ✅ Жёстко прибиваем общий лэйаут влево */
          html, body { text-align: left !important; }

          /* 1) Если шаблон использует flex-контейнеры и выравнивает контент вправо/по центру */
          body.vh .container,
          body.vh .page,
          body.vh .content,
          body.vh .sheet,
          body.vh .root,
          body.vh .wrapper {
            justify-content: flex-start !important;
            align-items: flex-start !important;
            text-align: left !important;
          }

          /* 2) Если шаблон “прижимает” блок вправо через auto-margin слева */
          body.vh .container,
          body.vh .page,
          body.vh .content,
          body.vh .sheet,
          body.vh .root,
          body.vh .wrapper {
            margin-left: 0 !important;
            margin-right: auto !important;
          }

          /* 3) Donut-ряд тоже строго влево */
          body.vh .donut-wrap {
            justify-content: flex-start !important;
            align-items: flex-start !important;
            text-align: left !important;
          }
        </style>
    """.trimIndent()

    /**
     * FALLBACK CSS: используется ТОЛЬКО если template.html не найден или сломан.
     * Не должен “подмешиваться” к нормальному шаблону.
     */
    private val FALLBACK_STYLE = """
        <style>
          .footer {
            margin-top: 24px;
            padding-top: 8px;
            border-top: 1px solid #E5E7EB;
            font-size: 11px;
            color: #6B7280;
          }

          table.phase-table { width: 100%; border-collapse: collapse; margin: 0 0 12px 0; }
          table.phase-table th, table.phase-table td { padding: 10px 0; }
          table.phase-table th.center, table.phase-table td.center { text-align: left; }
          table.phase-table th.num, table.phase-table td.num { text-align: right; white-space: nowrap; }

          /* FREE: компактная таблица */
          table.phase-table.free { margin: 0 0 8px 0; }
          table.phase-table.free th, table.phase-table.free td { padding: 6px 0; }
          table.phase-table.free tr.group-start td { border-top-width: 1px; }

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

          .h2-tight { margin: 0 0 8px 0; }

          /* Шаги расчёта (PRO) */
          .steps { margin-top: 10pt; }

          .calc-step {
            border: 1px solid #E5E7EB;
            border-radius: 10px;
            padding: 10pt 12pt;
            margin: 10pt 0;
            background: #FFFFFF;
          }

          .calc-step .rows {
            margin-top: 8pt;
            display: grid;
            grid-template-columns: 120px 1fr;
            gap: 6pt 10pt;
          }

          .calc-step .lbl {
            color: #6B7280;
            font-size: 12px;
            font-weight: 700;
          }

          .calc-step .val {
            font-size: 12.5px;
            color: #111827;
          }

          .mono {
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
            font-variant-numeric: tabular-nums;
          }

          .subst-list { margin: 0; padding-left: 16px; }
          .subst-list li { margin: 2px 0; }

          .assumptions { margin-top: 12pt; }
          .assumption-list { margin: 6pt 0 0 18px; padding: 0; }
          .assumption-list li { margin: 8pt 0; }
          .assump-title { font-size: 12.5px; color:#111827; }
          .assump-meta { font-size: 11.5px; color:#6B7280; margin-top:2pt; }
        </style>
    """.trimIndent()

    fun build(
        model: ReportModel,
        includeInlineNormatives: Boolean = false
    ): String {
        val htmlTemplate = runCatching { loadTemplate("report_pdf/template.html") }.getOrNull()

        val profileClass = when (model.profile) {
            ReportModel.ReportProfile.FREE -> "vh-free"
            ReportModel.ReportProfile.PRO -> "vh-pro"
        }

        val base = if (htmlTemplate.isNullOrBlank()) {
            // fallback уже содержит FALLBACK_STYLE, к нему докидываем runtime overrides
            FALLBACK_TEMPLATE
                .let { injectIntoHead(it, RUNTIME_OVERRIDES_STYLE) }
                .let { ensureBodyHasClass(it, "vh $profileClass") }
        } else {
            // нормальный путь: template.html + минимальные runtime overrides
            htmlTemplate
                .let { injectIntoHead(it, RUNTIME_OVERRIDES_STYLE) }
                .let { ensureBodyHasClass(it, "vh $profileClass") }
        }

        var html = base
            .replace("{{projectName}}", escapeHtml(model.header.projectName))
            .replace("{{date}}", escapeHtml(model.header.date))
            .replace("{{titleBlock}}", buildTitleBlock(model))
            .replace(
                "{{kpiBlock}}",
                buildKpiBlock(
                    model = model,
                    includeInlineNormatives = includeInlineNormatives && model.profile == ReportModel.ReportProfile.PRO
                )
            )
            .replace("{{donutSection}}", buildDonutSection(model.donut))
            .replace("{{legendSection}}", buildLegend(model.donut))

        val sectionsHtml = buildSectionsHtml(model)

        html = html
            // steps вставляем через phasesHtml/sectionsHtml (см. buildSectionsHtml), чтобы порядок был предсказуем.
            .replace("{{stepsHtml}}", "")
            .let { base2 ->
                if (base2.contains("{{explainHtml}}")) base2.replace("{{explainHtml}}", "")
                else base2
            }
            .replace("{{phasesHtml}}", sectionsHtml)
            .replace("{{phases}}", sectionsHtml)

        return html
    }

    private fun buildSectionsHtml(model: ReportModel): String {
        val phasesHtml = buildPhasesTables(
            phases = model.phases,
            showDevices = model.profile == ReportModel.ReportProfile.PRO
        )

        return when (model.profile) {
            ReportModel.ReportProfile.FREE -> phasesHtml
            ReportModel.ReportProfile.PRO -> {
                val stepsHtml = buildStepsSection(model)
                val assumptionsHtml = buildAssumptionsSection(model)
                phasesHtml + stepsHtml + assumptionsHtml
            }
        }
    }

    private fun buildStepsSection(model: ReportModel): String {
        if (model.profile != ReportModel.ReportProfile.PRO) return ""
        val steps = model.steps
        if (steps.isEmpty()) return ""

        return buildString {
            appendLine("""<div class="steps">""")
            appendLine("""<h2 class="h2-tight">Шаги расчёта</h2>""")
            steps.forEachIndexed { idx, step ->
                appendLine(renderStep(idx + 1, step))
            }
            appendLine("""</div>""")
        }
    }

    private fun renderStep(index: Int, step: ru.mugalimov.volthome.domain.model.CalcStep): String {
        val stepName = escapeHtml(step.name)
        val formula = escapeHtml(step.formula)

        val substitutionHtml = if (step.inputs.isEmpty()) {
            """<span class="muted">—</span>"""
        } else {
            buildString {
                append("""<ul class="subst-list">""")
                step.inputs.forEach { inp ->
                    val name = escapeHtml(inp.name)
                    val v = formatValue(inp.value, inp.unit)
                    val unit = escapeHtml(inp.unit)
                    val tail = if (unit.isBlank()) "" else " $unit"
                    append("""<li><span class="mono">$name = $v$tail</span></li>""")
                }
                append("""</ul>""")
            }
        }

        val outV = formatValue(step.output.value, step.output.unit)
        val outUnit = escapeHtml(step.output.unit)
        val outTail = if (outUnit.isBlank()) "" else " $outUnit"

        return """
          <div class="calc-step">
            <div class="step-title">
              <span class="pill info">Шаг $index</span>
              <span>$stepName</span>
            </div>
            <div class="rows">
              <div class="lbl">Формула</div>
              <div class="val mono">$formula</div>

              <div class="lbl">Подстановка</div>
              <div class="val">$substitutionHtml</div>

              <div class="lbl">Результат</div>
              <div class="val mono"><b>$outV$outTail</b></div>
            </div>
          </div>
        """.trimIndent()
    }

    private fun buildAssumptionsSection(model: ReportModel): String {
        if (model.profile != ReportModel.ReportProfile.PRO) return ""
        val list = model.assumptions
        if (list.isEmpty()) return ""

        fun fmtNum(v: Double?): String = v?.let { df2.format(it) } ?: "—"

        return buildString {
            appendLine("""<div class="assumptions">""")
            appendLine("""<h2 class="h2-tight">Принятые инженерные допущения</h2>""")
            appendLine("""<ul class="assumption-list">""")
            list.forEach { a ->
                appendLine(
                    """
                    <li>
                      <div class="assump-title"><b>${escapeHtml(a.subject)}</b> — ${escapeHtml(a.message)}</div>
                      <div class="assump-meta muted">
                        ${escapeHtml(a.kind.name)}${
                        a.source?.let { " • ${escapeHtml(it.name)}" } ?: ""
                    }${
                        if (a.original != null || a.applied != null)
                            " • ${fmtNum(a.original)} → ${fmtNum(a.applied)}"
                        else ""
                    }
                      </div>
                    </li>
                    """.trimIndent()
                )
            }
            appendLine("""</ul></div>""")
        }
    }

    private fun buildKpiBlock(model: ReportModel, includeInlineNormatives: Boolean): String {
        val currents = model.kpis.headlineCurrents

        val iA = formatA(currents["A"])
        val iB = formatA(currents["B"])
        val iC = formatA(currents["C"])

        val isSingle = model.donut is DonutModel.IncomerLoad
        val incomerHuman = humanizeIncomer(model.header.incomerLabel)

        fun inlineNorm(key: InlineNormatives.FactKey): String =
            InlineNormatives.forFact(key)?.let { " — ${escapeHtml(it)}" }.orEmpty()

        fun hasMainRcdLabel(raw: String): Boolean {
            val s = raw.lowercase(Locale.getDefault())
            return s.contains("rcd") || s.contains("узо") || s.contains("rcbo")
        }

        val incomerNormKey = if (hasMainRcdLabel(model.header.incomerLabel)) {
            InlineNormatives.FactKey.MAIN_RCD
        } else {
            InlineNormatives.FactKey.INCOMER_SCHEME
        }

        return buildString {
            append("""<div class="kpi">""")
            append("""<div>Дата: <b>${escapeHtml(model.header.date)}</b></div>""")

            append(
                """<div>Вводной аппарат: <b>${escapeHtml(incomerHuman)}</b>${
                    if (includeInlineNormatives) inlineNorm(incomerNormKey) else ""
                }</div>"""
            )

            model.kpis.installedPowerW?.let { w ->
                val kw = w / 1000.0
                append(
                    """<div>Установленная мощность: <b>${df1.format(kw)} кВт</b>${
                        if (includeInlineNormatives) inlineNorm(InlineNormatives.FactKey.INSTALLED_POWER) else ""
                    }</div>"""
                )
            }

            model.kpis.calculatedPowerW?.let { w ->
                val kw = w / 1000.0
                append(
                    """<div>Расчётная нагрузка: <b>${df1.format(kw)} кВт</b>${
                        if (includeInlineNormatives) inlineNorm(InlineNormatives.FactKey.CALCULATED_LOAD) else ""
                    }</div>"""
                )
            }

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

    private fun buildTitleBlock(model: ReportModel): String {
        val modeLabel = when (model.header.phaseMode) {
            ReportModel.PhaseMode.SINGLE -> "1ф"
            ReportModel.PhaseMode.THREE -> "3ф"
        }

        if (model.profile == ReportModel.ReportProfile.FREE) {
            return """
                <div class="doc-title">
                  <h2 class="h2-tight">Титул</h2>
                  <div class="kpi">
                    <div>Дата: <b>${escapeHtml(model.header.date)}</b></div>
                    <div>Режим: <b>$modeLabel</b></div>
                  </div>
                </div>
            """.trimIndent()
        }

        val profileLabel = "PRO"
        val version = model.header.appVersion.takeIf { it.isNotBlank() } ?: "—"

        return """
            <div class="doc-title">
              <h2 class="h2-tight">Титул</h2>
              <div class="kpi">
                <div>Дата: <b>${escapeHtml(model.header.date)}</b></div>
                <div>Режим: <b>$modeLabel</b></div>
                <div>Профиль: <b>$profileLabel</b></div>
                <div>Версия приложения: <b>${escapeHtml(version)}</b></div>
              </div>
            </div>
        """.trimIndent()
    }

    private fun buildPhasesTables(
        phases: List<ReportPhase>,
        showDevices: Boolean
    ): String {
        if (phases.isEmpty()) return ""

        val ordered = phases.sortedWith(compareBy({ phaseOrderKey(it.name) }, { it.name }))

        return buildString {
            ordered.forEach { phase ->
                appendLine("""<div class="section">""")
                appendLine("""<h2 class="h2-tight phase-title">${escapeHtml(phase.name)}</h2>""")

                if (phase.groups.isEmpty()) {
                    appendLine("""<div class="group empty">—</div>""")
                    appendLine("""</div>""")
                    return@forEach
                }

                val tableClass = if (showDevices) "phase-table" else "phase-table free"
                appendLine("""<table class="$tableClass">""")

                if (showDevices) {
                    appendLine(
                        """<thead class="phase-head"><tr><th class="center">Устройство</th><th class="num">Мощность</th><th class="num">Ток</th></tr></thead>"""
                    )
                } else {
                    appendLine("""<thead class="phase-head"><tr><th class="center">Группа</th></tr></thead>""")
                }

                val groups = phase.groups.sortedBy { extractGroupNumber(it.title) ?: Int.MAX_VALUE }

                if (!showDevices) {
                    // ✅ FREE “по-взрослому”: один tbody, никаких group-block.
                    // Это снимает капризы Android PDF по разрывам + убирает неделимые блоки.
                    appendLine("<tbody>")
                    groups.forEach { g ->
                        val metaLine = buildGroupMeta(g)
                        appendLine(
                            """
                            <tr class="group-start">
                              <td class="center">
                                <span class="chip">${escapeHtml(g.title)}</span>
                                ${
                                if (metaLine.isNotEmpty())
                                    """<span class="meta-inline">${escapeHtml(metaLine)}</span>"""
                                else ""
                            }
                              </td>
                            </tr>
                            """.trimIndent()
                        )
                    }
                    appendLine("</tbody>")
                } else {
                    // ✅ PRO: group-block сохраняем (и только тут он реально нужен).
                    groups.forEach { g: ReportGroup ->
                        val metaLine = buildGroupMeta(g)
                        appendLine("""<tbody class="group-block">""")

                        appendLine(
                            """
                            <tr class="group-start">
                              <td class="center" colspan="3">
                                <span class="chip">${escapeHtml(g.title)}</span>
                                ${
                                if (metaLine.isNotEmpty())
                                    """<span class="meta-inline">${escapeHtml(metaLine)}</span>"""
                                else ""
                            }
                              </td>
                            </tr>
                            """.trimIndent()
                        )

                        val devices = g.devices.sortedWith(
                            compareBy<ReportDevice>({ it.name.lowercase(Locale.getDefault()) })
                                .thenBy { (it.powerW ?: 0.0) }
                                .thenBy { (it.currentA ?: 0.0) }
                        )

                        devices.forEach { d ->
                            val (p, c) = pickDeviceNumbers(d)
                            appendLine(
                                """
                                <tr class="dev">
                                  <td class="center">${escapeHtml(d.name)}</td>
                                  <td class="num">${p ?: ""}</td>
                                  <td class="num">${c ?: ""}</td>
                                </tr>
                                """.trimIndent()
                            )
                        }

                        appendLine("</tbody>")
                    }
                }

                appendLine("</table>")
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
        var pw: Double? = d.powerW?.toDouble()
        var ia: Double? = d.currentA

        if (pw == null || ia == null) {
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

            if (pw == null) pw = getNumber(d, powerFieldAliases)?.toDouble()
            if (ia == null) ia = getNumber(d, currentFieldAliases)?.toDouble()

            if ((pw == null || ia == null) && d.spec.isNotBlank()) {
                val parsed = parseSpec(d.spec)
                if (pw == null) pw = parsed.first?.toDouble()
                if (ia == null) ia = parsed.second
            }

            if (ia == null && pw != null) ia = pw / VOLTAGE_DEFAULT
        }

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
            ${row("Фаза A", "a", a, pa, a >= maxVal - em)}
            ${row("Фаза B", "b", b, pb, b >= maxVal - em)}
            ${row("Фаза C", "c", c, pc, c >= maxVal - em)}
          </div>
        """.trimIndent()
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
                    return "M ${fmtUS(sx)} ${fmtUS(sy)} A ${fmtUS(r)} ${fmtUS(r)} 0 $large 1 ${fmtUS(ex)} ${fmtUS(ey)}"
                }

                val pathA = arcPath(start, pa / 100.0 * 360.0).also { start += pa / 100.0 * 360.0 }
                val pathB = arcPath(start, pb / 100.0 * 360.0).also { start += pb / 100.0 * 360.0 }
                val pathC = arcPath(start, pc / 100.0 * 360.0)

                // ✅ Слева, без align-items:center
                """
                <div style="display:flex;flex-direction:column;align-items:flex-start;margin-top:6pt;">
                  <svg viewBox="0 0 42 42" width="210" height="210" role="img" aria-label="Баланс фаз">
                    <circle cx="21" cy="21" r="$r" fill="none" stroke="#eeeeee" stroke-width="5"/>
                    <path d="$pathA" fill="none" stroke="#f2cc66" stroke-width="5" stroke-linecap="butt"/>
                    <path d="$pathB" fill="none" stroke="#5bbf72" stroke-width="5" stroke-linecap="butt"/>
                    <path d="$pathC" fill="none" stroke="#f26d6d" stroke-width="5" stroke-linecap="butt"/>
                  </svg>
                  <div class="donut-caption" style="align-self:flex-start;">Баланс фаз</div>
                </div>
                """.trimIndent()
            }

            is DonutModel.IncomerLoad -> {
                val used = max(model.usedA, 0.0)
                val limit = max(model.limitA, eps)
                val pct = (used / limit * 100.0).coerceIn(0.0, 100.0)
                val reserve = max(limit - used, 0.0)

                // ✅ Слева: margin без auto + подписи text-align:left
                """
                <div style="position:relative;width:240px;height:240px;margin:8pt 0 0;">
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
                <div class="donut-caption" style="text-align:left;">Загрузка вводного автомата</div>
                <div class="donut-sub muted" style="text-align:left;">Всего: ${df2.format(used)} $UNIT_A из ${df0.format(limit)} $UNIT_A • ${df0.format(pct)}% • Запас ${df2.format(reserve)} $UNIT_A</div>
                """.trimIndent()
            }
        }
    }

    private fun formatValue(value: Double, unitRaw: String): String {
        val u = unitRaw.trim().lowercase(Locale.getDefault())
        return when {
            u.contains("вт") && !u.contains("квт") -> df0.format(value)
            u.contains("квт") -> df1.format(value)
            else -> df2.format(value)
        }
    }

    private fun formatA(v: Double?): String =
        when (v) {
            null -> ""
            else -> "${df2.format(v)} $UNIT_A"
        }

    private fun fmtUS(v: Double): String = String.format(Locale.US, "%.6f", v)

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun loadTemplate(path: String): String {
        context.assets.open(path).use { ins ->
            BufferedReader(InputStreamReader(ins)).use { br ->
                return br.readText()
            }
        }
    }

    private fun phaseOrderKey(name: String): Int {
        val n = name.uppercase(Locale.getDefault())
        return when {
            n.contains("A") -> 0
            n.contains("B") -> 1
            n.contains("C") -> 2
            else -> 99
        }
    }

    private fun extractGroupNumber(title: String): Int? {
        val m = Regex("""#\s*(\d+)""").find(title) ?: return null
        return m.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun humanizeIncomer(raw0: String): String {
        if (raw0.isBlank()) return ""
        val parts = raw0.replace('•', ',').replace('·', ',')
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val out = mutableListOf<String>()
        for (p in parts) {
            val lower = p.lowercase(Locale.getDefault())

            if (p.equals("MCB_ONLY", true) || lower.contains("mcb only")) {
                out += "Автомат"; continue
            } else if (
                p.equals("MCB_PLUS_RCD", true) ||
                lower.contains("mcb+rcd") ||
                lower.contains("mcb_plus_rcd")
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
                val amps = if (ka != null) {
                    (ka.replace(',', '.').toDoubleOrNull() ?: 0.0) * 1000.0
                } else {
                    onlyNum?.toDoubleOrNull()
                }
                if (amps != null) {
                    out += "отключающая способность ${df0.format(amps)} $UNIT_A"; continue
                }
            }

            if (lower.contains("rcd")) {
                val type = Regex("""rcd\s*([A-Z]+)""", RegexOption.IGNORE_CASE)
                    .find(p)?.groupValues?.getOrNull(1)?.uppercase()
                val sens = Regex("""(\d+)\s*mA""", RegexOption.IGNORE_CASE)
                    .find(p)?.groupValues?.getOrNull(1)
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

    private fun injectIntoHead(html: String, styleBlock: String): String {
        if (html.contains("id=\"vh-runtime-overrides\"")) return html
        return if (html.contains("</head>", ignoreCase = true)) {
            html.replace(Regex("</head>", RegexOption.IGNORE_CASE), "$styleBlock</head>")
        } else {
            // если head сломан — просто прицепим в начало
            "$styleBlock$html"
        }
    }

    private fun ensureBodyHasClass(html: String, classesToAdd: String): String {
        // 1) если body с class уже есть — дописываем туда
        val bodyWithClass = Regex(
            """<body\b([^>]*)\bclass\s*=\s*["']([^"']*)["']([^>]*)>""",
            RegexOption.IGNORE_CASE
        )
        val m = bodyWithClass.find(html)
        if (m != null) {
            val before = m.groupValues[1]
            val existing = m.groupValues[2]
            val after = m.groupValues[3]
            val merged = mergeClasses(existing, classesToAdd)
            val replaced = """<body$before class="$merged"$after>"""
            return html.replaceRange(m.range, replaced)
        }

        // 2) иначе — добавляем class атрибут
        val bodyNoClass = Regex("""<body\b([^>]*)>""", RegexOption.IGNORE_CASE)
        val m2 = bodyNoClass.find(html) ?: return html
        val attrs = m2.groupValues[1]
        val replaced = """<body$attrs class="${escapeAttr(classesToAdd)}">"""
        return html.replaceRange(m2.range, replaced)
    }

    private fun mergeClasses(existing: String, toAdd: String): String {
        val set = linkedSetOf<String>()
        existing.split(' ').map { it.trim() }.filter { it.isNotEmpty() }.forEach { set += it }
        toAdd.split(' ').map { it.trim() }.filter { it.isNotEmpty() }.forEach { set += it }
        return set.joinToString(" ")
    }

    private fun escapeAttr(s: String): String = s.replace("\"", "&quot;")

    private val FALLBACK_TEMPLATE = """
        <!doctype html>
        <html lang="ru">
        <head><meta charset="utf-8"/><title>VoltHome — Экспликация</title>$FALLBACK_STYLE</head>
        <body>
          <h2>VoltHome — Экспликация</h2>
          {{titleBlock}}
          {{kpiBlock}}
          <div class="donut-wrap">
            <div class="donut-col">{{donutSection}}</div>
            <div class="legend-col">{{legendSection}}</div>
          </div>
          <hr/>
          <div>{{phasesHtml}}</div>
          {{watermark}}
        </body>
        </html>
    """.trimIndent()
}