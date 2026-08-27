package ru.mugalimov.volthome.ui.utilities

import android.content.Context
import ru.mugalimov.volthome.domain.model.CoefficientSource
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.report.DonutModel
import ru.mugalimov.volthome.domain.model.report.ReportDevice
import ru.mugalimov.volthome.domain.model.report.ReportGroup
import ru.mugalimov.volthome.domain.model.report.ReportModel
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import ru.mugalimov.volthome.domain.model.report.professional.ReportWarningItem
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class HtmlReportBuilder private constructor(
    private val context: Context?,
    private val templateOverride: String?
) {

    constructor(context: Context) : this(context = context, templateOverride = null)

    internal constructor(template: String) : this(context = null, templateOverride = template)

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
        val htmlTemplate = templateOverride
            ?: runCatching { loadTemplate("report_pdf/template.html") }.getOrNull()

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
            .replace("{{projectName}}", escapeHtml(model.header.projectName.ifBlank { "Проект ВольтХом" }))
            .replace("{{reportDate}}", escapeHtml(model.header.date))
            .replace("{{appVersion}}", escapeHtml(model.header.appVersion.ifBlank { "—" }))
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
            .replace("{{balanceConclusion}}", buildBalanceConclusion(model))
            .replace("{{statusSection}}", buildStatusSection(model))
            .replace("{{warningsSection}}", buildWarningsSection(model))
            .replace("{{methodologySection}}", buildStepsSection(model))
            .replace("{{assumptionsSection}}", buildAssumptionsSection(model))
            .replace("{{normsSection}}", buildNormsSection(model))
            .replace("{{legalBlock}}", buildLegalBlock(model))

        val phasesHtml = buildPhasesTables(
            phases = model.phases,
            showDevices = model.profile == ReportModel.ReportProfile.PRO
        )

        html = html
            .replace("{{phasesHtml}}", phasesHtml)
            .replace("{{phases}}", phasesHtml)

        return html
    }

    private fun buildStepsSection(model: ReportModel): String {
        if (model.profile != ReportModel.ReportProfile.PRO) return ""
        val steps = model.steps
        if (steps.isEmpty()) {
            return """
                <div class="panel">
                    <div class="panel-title">Методика расчёта</div>
                    <div class="muted">Расчёт выполнен по данным проекта. Детализированные шаги для этого проекта отсутствуют.</div>
                    ${buildMethodLimitations()}
                </div>
            """.trimIndent()
        }

        return buildString {
            appendLine("""<div class="subsection-title">Как сформирован результат</div>""")
            appendLine("""<div class="method-grid">""")
            steps.forEachIndexed { idx, step ->
                appendLine(renderStep(idx + 1, step))
            }
            appendLine("""</div>""")
            appendLine(buildProtectionMethod())
            appendLine(buildMethodLimitations())
        }
    }

    private fun buildProtectionMethod(): String =
        """
        <div class="panel">
          <div class="panel-title">Выбор аппаратов и распределение</div>
          <div class="list-copy">
            Автомат группы выбирается по установленному току и минимальному номиналу для типа линии;
            кабель — после автомата из поддерживаемой продуктовой матрицы. УЗО 30 мА назначается линиям
            особых помещений, группам с элементом «Розетка бытовая» и самостоятельным группам, в которых
            устройства подключаются через розетку. В трёхфазном режиме группы распределяются по минимальному
            результирующему перекосу.
          </div>
        </div>
        """.trimIndent()

    private fun buildMethodLimitations(): String =
        """
        <div class="panel">
          <div class="panel-title">Границы расчётной модели</div>
          <div class="list-copy">
            Для линий с введёнными параметрами учитываются длина и материал кабеля, изоляция, способ прокладки, температура,
            группировка и падение напряжения. Без длины сечение остаётся предварительным. Не рассчитываются
            ток короткого замыкания, петля повреждения, время автоматического отключения и согласование конкретных серий аппаратов.
            Перед монтажом результат должен быть проверен специалистом по исходным данным объекта.
          </div>
        </div>
        """.trimIndent()

    private fun renderStep(index: Int, step: ru.mugalimov.volthome.domain.model.CalcStep): String {
        val stepName = escapeHtml(step.name)
        val formula = escapeHtml(step.formula)

        val outV = formatValue(step.output.value, step.output.unit)
        val outUnit = escapeHtml(step.output.unit)
        val outTail = if (outUnit.isBlank()) "" else " $outUnit"
        val explanation = stepExplanation(step.name)

        return """
          <div class="method-card">
            <div class="method-index">$index</div>
            <div class="method-title">$stepName</div>
            <div class="method-copy">${escapeHtml(explanation)}</div>
            <div class="formula-box">Формула: $formula</div>
            <div class="method-result">$outV$outTail</div>
          </div>
        """.trimIndent()
    }

    private fun stepExplanation(name: String): String {
        val normalized = name.lowercase(Locale.ROOT)
        return when {
            normalized.contains("установлен") ->
                "Сложены паспортные мощности всех потребителей, включённых в расчётную модель проекта."
            normalized.contains("нагруз") || normalized.contains("спрос") ->
                "К мощности каждого потребителя применён коэффициент спроса, после чего рассчитанные вклады суммированы."
            normalized.contains("ток") ->
                "Расчётная мощность преобразована в ток с учётом напряжения и параметров подключённой нагрузки."
            else ->
                "Шаг использует исходные данные проекта и формирует итоговое значение без изменения пользовательских параметров."
        }
    }

    private fun buildBalanceConclusion(model: ReportModel): String {
        val text = when (val donut = model.donut) {
            is DonutModel.PhaseDistribution -> {
                val values = donut.valuesA
                    .filterKeys { it == Phase.A || it == Phase.B || it == Phase.C }
                val maxEntry = values.maxByOrNull { it.value }
                val minEntry = values.minByOrNull { it.value }
                if (maxEntry == null || minEntry == null) {
                    "Данных для сравнения фаз недостаточно."
                } else {
                    val delta = maxEntry.value - minEntry.value
                    "Наибольшая нагрузка приходится на фазу ${maxEntry.key.name}. Разница между максимальным и минимальным фазным током: ${df2.format(delta)} $UNIT_A."
                }
            }

            is DonutModel.IncomerLoad -> {
                val reserve = (donut.limitA - donut.usedA).coerceAtLeast(0.0)
                val pct = if (donut.limitA > eps) donut.usedA / donut.limitA * 100.0 else 0.0
                "Расчётная загрузка вводного аппарата: ${df0.format(pct)}%. Токовый резерв: ${df2.format(reserve)} $UNIT_A."
            }
        }

        return """<div class="balance-conclusion">${escapeHtml(text)}</div>"""
    }

    private fun buildStatusSection(model: ReportModel): String {
        val warnings = model.professional?.warnings.orEmpty()
        val highest = warnings.maxByOrNull { it.severity.rank() }?.severity

        val (css, icon, title, copy) = when (highest) {
            ReportWarningItem.Severity.CRITICAL -> listOf(
                "critical", "!", "Требуется внимание",
                "В расчётных данных обнаружены критические замечания. Перед реализацией проекта нужна инженерная проверка."
            )
            ReportWarningItem.Severity.WARNING -> listOf(
                "warning", "!", "Есть замечания",
                "Расчёт сформирован, но отдельные параметры требуют проверки перед монтажом."
            )
            ReportWarningItem.Severity.INFO -> listOf(
                "", "i", "Расчёт сформирован",
                "В отчёте присутствуют информационные замечания к исходным данным."
            )
            null -> listOf(
                "", "✓", "Расчёт сформирован",
                "Критические замечания в доступных расчётных данных не обнаружены."
            )
        }

        return """
            <div class="status-card $css">
              <div class="status-icon">$icon</div>
              <div>
                <div class="status-title">$title</div>
                <div class="status-copy">$copy</div>
              </div>
            </div>
        """.trimIndent()
    }

    private fun buildWarningsSection(model: ReportModel): String {
        val warnings = model.professional?.warnings.orEmpty()
            .sortedByDescending { it.severity.rank() }

        if (warnings.isEmpty()) return ""

        val visible = warnings.take(5)
        return buildString {
            appendLine("""<div class="warning-list">""")
            visible.forEach { item ->
                val badgeClass = when (item.severity) {
                    ReportWarningItem.Severity.CRITICAL -> "critical"
                    ReportWarningItem.Severity.WARNING -> ""
                    ReportWarningItem.Severity.INFO -> "info"
                }
                val badgeText = when (item.severity) {
                    ReportWarningItem.Severity.CRITICAL -> "Важно"
                    ReportWarningItem.Severity.WARNING -> "Проверить"
                    ReportWarningItem.Severity.INFO -> "Справочно"
                }
                appendLine(
                    """
                    <div class="warning-item">
                      <div><span class="warning-badge $badgeClass">$badgeText</span></div>
                      <div>
                        <div class="warning-title">${escapeHtml(item.title)}</div>
                        <div class="warning-copy">${escapeHtml(item.message)}</div>
                      </div>
                    </div>
                    """.trimIndent()
                )
            }
            if (warnings.size > visible.size) {
                appendLine("""<div class="warning-copy">Дополнительных замечаний: ${warnings.size - visible.size}.</div>""")
            }
            appendLine("""</div>""")
        }
    }

    private fun ReportWarningItem.Severity.rank(): Int = when (this) {
        ReportWarningItem.Severity.INFO -> 0
        ReportWarningItem.Severity.WARNING -> 1
        ReportWarningItem.Severity.CRITICAL -> 2
    }

    private fun assumptionSubject(raw: String): String {
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "demandratio", "demand_ratio" -> "Коэффициент спроса"
            "powerfactor", "power_factor", "cosφ", "cosphi" -> "Коэффициент мощности"
            "u", "voltage" -> "Расчётное напряжение"
            else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    private fun assumptionSourceLabel(source: CoefficientSource?): String {
        return when (source) {
            CoefficientSource.USER -> "Использовано значение, заданное пользователем."
            CoefficientSource.DEFAULT -> "Использовано значение из профиля устройства."
            null -> ""
        }
    }

    private fun buildAssumptionsSection(model: ReportModel): String {
        if (model.profile != ReportModel.ReportProfile.PRO) return ""
        val list = model.assumptions
        if (list.isEmpty()) return ""

        fun fmtNum(v: Double?): String = v?.let { df2.format(it) } ?: "—"

        return buildString {
            appendLine("""<div class="subsection-title">Принятые инженерные допущения</div>""")
            appendLine("""<ul class="assumption-list">""")
            list.distinctBy { "${it.subject}|${it.message}|${it.original}|${it.applied}" }.forEach { a ->
                val values = if (a.original != null || a.applied != null) {
                    "Исходное значение: ${fmtNum(a.original)}; применено: ${fmtNum(a.applied)}."
                } else {
                    assumptionSourceLabel(a.source)
                }
                appendLine(
                    """
                    <li class="assumption-item">
                      <div class="list-icon">i</div>
                      <div>
                        <div class="list-title">${escapeHtml(assumptionSubject(a.subject))}</div>
                        <div class="list-copy">${escapeHtml(a.message)} ${escapeHtml(values)}</div>
                      </div>
                    </li>
                    """.trimIndent()
                )
            }
            appendLine("""</ul>""")
        }
    }

    private fun buildNormsSection(model: ReportModel): String {
        if (model.profile != ReportModel.ReportProfile.PRO) return ""
        val refs = model.professional?.normRefs.orEmpty()
        if (refs.isEmpty()) return ""

        return buildString {
            appendLine("""<div class="subsection-title">Нормативная база</div>""")
            appendLine("""<ul class="norm-list">""")
            refs.forEach { ref ->
                val details = listOfNotNull(
                    ref.section?.takeIf { it.isNotBlank() }?.let { "Раздел $it" },
                    ref.note?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                appendLine(
                    """
                    <li class="norm-item">
                      <div class="list-icon">§</div>
                      <div>
                        <div class="list-title">${escapeHtml(ref.source)}</div>
                        ${if (details.isBlank()) "" else "<div class=\"list-copy\">${escapeHtml(details)}</div>"}
                      </div>
                    </li>
                    """.trimIndent()
                )
            }
            appendLine("""</ul>""")
        }
    }

    /**
     * Постоянный legal/disclaimer block внутри экспортируемого PDF.
     *
     * Важно:
     * - это не watermark "для красоты";
     * - это часть документа;
     * - текст должен жить в самом экспорте.
     */
    private fun buildLegalBlock(model: ReportModel): String {
        val profileLabel = when (model.profile) {
            ReportModel.ReportProfile.FREE -> "FREE"
            ReportModel.ReportProfile.PRO -> "PRO"
        }

        return """
            <div class="legal-block">
              <b>О статусе документа.</b>
              Отчёт сформирован приложением «ВольтХом» в профиле <b>${escapeHtml(profileLabel)}</b> и отражает расчётную модель проекта на момент экспорта.
              Документ не является исполнительной документацией, актом допуска или подтверждением соответствия монтажа требованиям норм без отдельной инженерной проверки и проверки на объекте.
            </div>
        """.trimIndent()
    }

    private fun buildKpiBlock(model: ReportModel, includeInlineNormatives: Boolean): String {
        val currents = model.kpis.headlineCurrents
        val maxCurrent = currents.values.maxOrNull()
        val maxPhase = currents.maxByOrNull { it.value }?.key

        fun card(label: String, value: String, note: String): String = """
            <div class="kpi-card">
              <div class="kpi-label">${escapeHtml(label)}</div>
              <div class="kpi-value">${escapeHtml(value)}</div>
              <div class="kpi-note">${escapeHtml(note)}</div>
            </div>
        """.trimIndent()

        val installed = model.kpis.installedPowerW?.let { "${df1.format(it / 1000.0)} кВт" } ?: "—"
        val calculated = model.kpis.calculatedPowerW?.let { "${df1.format(it / 1000.0)} кВт" } ?: "—"
        val groups = model.kpis.totalGroups?.toString() ?: "—"
        val current = maxCurrent?.let { "${df2.format(it)} $UNIT_A" } ?: "—"
        val currentNote = maxPhase?.let { "Максимум на фазе $it" } ?: "Максимальный фазный ток"

        return """
            <div class="kpi-grid">
              ${card("Установленная мощность", installed, "Сумма паспортных мощностей")}
              ${card("Расчётная нагрузка", calculated, "С учётом коэффициентов спроса")}
              ${card("Группы щита", groups, "Отходящие линии проекта")}
              ${card("Максимальный ток", current, currentNote)}
            </div>
        """.trimIndent()
    }

    private fun buildTitleBlock(model: ReportModel): String {
        val modeLabel = when (model.header.phaseMode) {
            ReportModel.PhaseMode.SINGLE -> "1ф"
            ReportModel.PhaseMode.THREE -> "3ф"
        }

        val profileLabel = if (model.profile == ReportModel.ReportProfile.PRO) "PRO" else "FREE"
        val incomer = humanizeIncomer(model.header.incomerLabel).ifBlank { "Вводной аппарат не указан" }

        return """
            <div class="hero-meta">
              <span class="hero-chip">Сеть: $modeLabel</span>
              <span class="hero-chip">Профиль: $profileLabel</span>
              <span class="hero-chip">${escapeHtml(incomer)}</span>
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
                val phaseLetter = phase.name.substringAfterLast(' ').take(1).uppercase(Locale.ROOT)
                val phaseClass = when (phaseLetter) {
                    "A" -> "phase-a"
                    "B" -> "phase-b"
                    "C" -> "phase-c"
                    else -> ""
                }
                val phaseCurrent = phase.totalCurrentA
                    ?: phase.groups.mapNotNull { it.calculatedCurrentA }.sum().takeIf { it > 0.0 }
                val phasePower = phase.installedPowerW
                    ?: phase.groups.mapNotNull { it.installedPowerW }.sum().takeIf { it > 0 }
                val groups = phase.groups.sortedBy { extractGroupNumber(it.title) ?: Int.MAX_VALUE }
                val chunks = if (groups.isEmpty()) listOf(emptyList()) else groups.chunked(4)

                chunks.forEachIndexed { chunkIndex, chunk ->
                    val continuation = if (chunkIndex == 0) "" else " <span class=\"muted\">· продолжение</span>"
                    appendLine("""<div class="phase-block">""")
                    appendLine(
                        """
                        <div class="phase-header">
                          <div class="phase-name">
                            <span class="phase-mark $phaseClass">${escapeHtml(phaseLetter)}</span>
                            ${escapeHtml(phase.name)}$continuation
                          </div>
                          <div class="phase-total">
                            ${phaseCurrent?.let { "${df2.format(it)} $UNIT_A" } ?: "—"}
                            ${phasePower?.let { " · ${df1.format(it / 1000.0)} кВт" } ?: ""}
                          </div>
                        </div>
                        """.trimIndent()
                    )

                    if (chunk.isEmpty()) {
                        appendLine("""<div class="empty-state">Для этой фазы группы не сформированы.</div>""")
                    } else {
                        chunk.forEach { group -> appendLine(renderGroupCard(group, showDevices)) }
                    }
                    appendLine("""</div>""")
                }
            }
        }
    }

    private fun renderGroupCard(g: ReportGroup, showDevices: Boolean): String {
        val number = g.number ?: extractGroupNumber(g.title)
        val purpose = g.purpose?.takeIf { it.isNotBlank() }
            ?: g.title.substringAfter('—', g.title).trim()
        val room = g.roomName?.takeIf { it.isNotBlank() } ?: "Помещение не указано"
        val protection = buildGroupProtection(g).ifBlank { "Не указано" }
        val cable = buildGroupCable(g).ifBlank { "Не указан" }
        val power = g.installedPowerW?.let { "${df1.format(it / 1000.0)} кВт" } ?: "—"
        val installedCurrent =
            g.installedCurrentA?.let { "${df2.format(it)} $UNIT_A" } ?: "—"
        val calculatedCurrent =
            g.calculatedCurrentA?.let { "${df2.format(it)} $UNIT_A" } ?: "—"
        val manualNotesHtml = if (g.manualNotes.isEmpty()) {
            ""
        } else {
            g.manualNotes.joinToString(
                prefix = """<div class="manual-note"><strong>Ручная настройка.</strong> """,
                separator = " ",
                postfix = "</div>"
            ) { escapeHtml(it) }
        }

        val devicesHtml = if (!showDevices || g.devices.isEmpty()) {
            ""
        } else {
            val rows = g.devices
                .sortedWith(compareBy<ReportDevice> { it.name.lowercase(Locale.getDefault()) })
                .joinToString(separator = "\n") { device ->
                    val (p, c) = pickDeviceNumbers(device)
                    """
                    <tr>
                      <td>${escapeHtml(device.name)}</td>
                      <td class="num">${p ?: "—"}</td>
                      <td class="num">${c ?: "—"}</td>
                    </tr>
                    """.trimIndent()
                }

            """
                <table class="device-table">
                  <thead>
                    <tr><th>Потребитель</th><th class="num">Мощность</th><th class="num">Ток</th></tr>
                  </thead>
                  <tbody>$rows</tbody>
                </table>
            """.trimIndent()
        }

        return """
            <article class="group-card">
              <div class="group-head">
                <div>
                  <div class="group-title">${if (number == null) "Группа" else "Группа $number"} · ${escapeHtml(purpose)}</div>
                  <div class="group-subtitle">${escapeHtml(room)}</div>
                </div>
                <div>
                  <div class="group-fact-label">Защита</div>
                  <div class="group-fact-value">${escapeHtml(protection)}</div>
                </div>
                <div>
                  <div class="group-fact-label">Кабель</div>
                  <div class="group-fact-value">${escapeHtml(cable)}</div>
                </div>
                <div>
                  <div class="group-fact-label">Нагрузка</div>
                  <div class="group-fact-value">$power · Iуст $installedCurrent · Iрасч $calculatedCurrent</div>
                </div>
              </div>
              $manualNotesHtml
              $devicesHtml
            </article>
        """.trimIndent()
    }

    private fun buildGroupProtection(g: ReportGroup): String {
        val breaker = listOfNotNull(
            g.switchLabel,
            g.groupSwitchLabel,
            g.apparatusLabel,
            g.protectionLabel,
            g.breakerLabel,
            g.deviceLabel
        ).firstOrNull { it.isNotBlank() }

        return listOfNotNull(breaker, g.rcdLabel?.takeIf { it.isNotBlank() })
            .distinct()
            .joinToString(" + ")
    }

    private fun buildGroupCable(g: ReportGroup): String {
        return listOfNotNull(
            g.cableLabel,
            g.lineLabel,
            g.wireLabel,
            g.cableInfo,
            g.lineInfo
        ).firstOrNull { it.isNotBlank() }.orEmpty()
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
        return when (donut) {
            is DonutModel.PhaseDistribution -> {
                val a = donut.valuesA[Phase.A] ?: 0.0
                val b = donut.valuesA[Phase.B] ?: 0.0
                val c = donut.valuesA[Phase.C] ?: 0.0

                val total = max(a + b + c, eps)
                val pa = a / total * 100.0
                val pb = b / total * 100.0
                val pc = 100.0 - pa - pb

                fun row(label: String, cls: String, amp: Double, pct: Double): String = """
                    <div class="row">
                      <div><span class="dot $cls"></span>$label</div>
                      <div class="num">${df2.format(amp)} $UNIT_A · ${df0.format(pct)}%</div>
                    </div>
                """.trimIndent()

                """
                  <div class="legend">
                    ${row("Фаза A", "a", a, pa)}
                    ${row("Фаза B", "b", b, pb)}
                    ${row("Фаза C", "c", c, pc)}
                  </div>
                """.trimIndent()
            }

            is DonutModel.IncomerLoad -> {
                val used = max(donut.usedA, 0.0)
                val limit = max(donut.limitA, eps)
                val reserve = max(limit - used, 0.0)
                """
                  <div class="legend">
                    <div class="row"><div>Расчётный ток</div><div class="num">${df2.format(used)} $UNIT_A</div></div>
                    <div class="row"><div>Номинал ввода</div><div class="num">${df2.format(limit)} $UNIT_A</div></div>
                    <div class="row"><div>Резерв</div><div class="num">${df2.format(reserve)} $UNIT_A</div></div>
                  </div>
                """.trimIndent()
            }
        }
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

                """
                  <svg class="donut-svg" viewBox="0 0 42 42" role="img" aria-label="Баланс фаз">
                    <circle cx="21" cy="21" r="$r" fill="none" stroke="#eeeeee" stroke-width="5"/>
                    <path d="$pathA" fill="none" stroke="#f2cc66" stroke-width="5" stroke-linecap="butt"/>
                    <path d="$pathB" fill="none" stroke="#5bbf72" stroke-width="5" stroke-linecap="butt"/>
                    <path d="$pathC" fill="none" stroke="#f26d6d" stroke-width="5" stroke-linecap="butt"/>
                    <text class="donut-center-value" x="21" y="20" text-anchor="middle">${df1.format(total)} А</text>
                    <text class="donut-center-caption" x="21" y="24" text-anchor="middle">сумма токов</text>
                  </svg>
                """.trimIndent()
            }

            is DonutModel.IncomerLoad -> {
                val used = max(model.usedA, 0.0)
                val limit = max(model.limitA, eps)
                val pct = (used / limit * 100.0).coerceIn(0.0, 100.0)
                val reserve = max(limit - used, 0.0)

                """
                  <svg class="donut-svg" viewBox="0 0 42 42" role="img" aria-label="Загрузка вводного автомата">
                    <circle cx="21" cy="21" r="15.915" fill="none" stroke="#eeeeee" stroke-width="7"/>
                    <circle cx="21" cy="21" r="15.915" fill="none"
                      stroke="#3156d3" stroke-width="7" stroke-linecap="butt"
                      stroke-dasharray="${fmtUS(pct)} ${fmtUS(100.0 - pct)}" stroke-dashoffset="25"/>
                    <text class="donut-center-value" x="21" y="20" text-anchor="middle">${df0.format(pct)}%</text>
                    <text class="donut-center-caption" x="21" y="24" text-anchor="middle">загрузка</text>
                  </svg>
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
        val safeContext = checkNotNull(context) { "Android context is required to load report assets" }
        safeContext.assets.open(path).use { ins ->
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
