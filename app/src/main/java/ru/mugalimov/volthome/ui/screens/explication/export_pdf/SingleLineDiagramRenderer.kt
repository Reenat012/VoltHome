package ru.mugalimov.volthome.ui.screens.explication.export_pdf

import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.singleline.SingleLineDiagram
import ru.mugalimov.volthome.domain.model.singleline.SingleLineGroupBlock
import ru.mugalimov.volthome.domain.model.singleline.SingleLinePhaseSection
import ru.mugalimov.volthome.domain.model.singleline.SingleLineProtectionBlock
import java.util.Locale

/**
 * HTML-renderer однолинейной схемы.
 *
 * Важно:
 * - renderer не рассчитывает токи, мощности, автоматы и кабели;
 * - renderer только отображает уже готовую модель SingleLineDiagram;
 * - layout рассчитан под A4 vertical;
 * - большие щиты разбиваются на несколько страниц;
 * - группы отображаются как ответвления от фазной шины.
 */
object SingleLineDiagramRenderer {

    private const val DISCLAIMER =
        "Однолинейная схема сформирована автоматически на основе данных, введённых пользователем в приложении ВольтХом. " +
                "Материал является инженерной визуализацией и не заменяет проектную документацию, разработанную уполномоченным специалистом."

    /**
     * Лимит веток на страницу.
     *
     * Это только ограничение PDF-layout, не инженерный расчёт.
     */
    private const val GROUPS_PER_PAGE = 12

    fun render(diagram: SingleLineDiagram): String {
        return """
            <!doctype html>
            <html lang="ru">
            <head>
                <meta charset="utf-8" />
                <style>
                    @page {
                        size: A4 portrait;
                        margin: 12mm;
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
                        page-break-after: always;
                        break-after: page;
                    }

                    .page:last-child {
                        page-break-after: auto;
                        break-after: auto;
                    }

                    .header {
                        display: flex;
                        justify-content: space-between;
                        align-items: flex-start;
                        border-bottom: 1px solid #d1d5db;
                        padding-bottom: 8px;
                        margin-bottom: 12px;
                    }

                    .title {
                        font-size: 18px;
                        font-weight: 800;
                        margin-bottom: 3px;
                    }

                    .subtitle {
                        font-size: 10px;
                        color: #4b5563;
                    }

                    .source {
                        font-size: 8px;
                        color: #6b7280;
                        text-align: right;
                        line-height: 1.35;
                    }

                    .incomer-chain {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        margin-bottom: 14px;
                        page-break-inside: avoid;
                        break-inside: avoid;
                    }

                    .chain-node {
                        min-width: 120px;
                        border: 1px solid #9ca3af;
                        border-radius: 8px;
                        padding: 6px 10px;
                        background: #ffffff;
                        text-align: center;
                        font-size: 10px;
                        font-weight: 800;
                    }

                    .chain-node-muted {
                        color: #4b5563;
                        font-weight: 700;
                        background: #f9fafb;
                    }

                    .chain-caption {
                        display: block;
                        margin-top: 2px;
                        font-size: 8px;
                        font-weight: 400;
                        color: #6b7280;
                    }

                    .v-line {
                        width: 1px;
                        height: 12px;
                        background: #6b7280;
                    }

                    .split-label {
                        margin-top: 2px;
                        font-size: 8px;
                        color: #6b7280;
                    }

                    .phase-section {
                        position: relative;
                        display: grid;
                        grid-template-columns: 34px 1fr;
                        column-gap: 10px;
                        margin-bottom: 12px;
                        page-break-inside: avoid;
                        break-inside: avoid;
                    }

                    .phase-marker {
                        position: relative;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                    }

                    .phase-badge {
                        width: 26px;
                        height: 26px;
                        border-radius: 6px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 11px;
                        font-weight: 900;
                        border: 1px solid #9ca3af;
                        background: #ffffff;
                        z-index: 2;
                    }

                    .phase-bus {
                        width: 3px;
                        flex: 1;
                        min-height: 36px;
                        border-radius: 99px;
                        margin-top: 4px;
                    }

                    .phase-a .phase-badge { background: #fff7cc; }
                    .phase-a .phase-bus { background: #f4c542; }

                    .phase-b .phase-badge { background: #dcfce7; }
                    .phase-b .phase-bus { background: #22c55e; }

                    .phase-c .phase-badge { background: #ffe4e6; }
                    .phase-c .phase-bus { background: #fb7185; }

                    .phase-3p .phase-badge { background: #f3f4f6; }
                    .phase-3p .phase-bus { background: #6b7280; }

                    .phase-content {
                        padding-top: 1px;
                    }

                    .phase-summary {
                        font-size: 8px;
                        color: #6b7280;
                        margin-bottom: 5px;
                    }

                    .branch {
                        display: grid;
                        grid-template-columns: 16px 42px 48px 56px 52px 1fr;
                        align-items: start;
                        gap: 5px;
                        min-height: 28px;
                        margin-bottom: 5px;
                        page-break-inside: avoid;
                        break-inside: avoid;
                    }

                    .tee {
                        position: relative;
                        height: 1px;
                        background: #6b7280;
                    }

                    .tee::before {
                        content: "";
                        position: absolute;
                        left: -10px;
                        top: -8px;
                        width: 1px;
                        height: 17px;
                        background: #6b7280;
                    }

                    .device-node {
                        border: 1px solid #d1d5db;
                        border-radius: 6px;
                        padding: 4px 5px;
                        background: #ffffff;
                        font-size: 8px;
                        font-weight: 800;
                        text-align: center;
                        white-space: nowrap;
                    }

                    .small-node {
                        border: 1px solid #e5e7eb;
                        border-radius: 6px;
                        padding: 4px 5px;
                        background: #ffffff;
                        font-size: 8px;
                        color: #374151;
                        white-space: nowrap;
                        text-align: center;
                    }

                    .load-node {
                        border: 1px solid #e5e7eb;
                        border-radius: 7px;
                        padding: 4px 6px;
                        background: #f9fafb;
                        font-size: 8px;
                        color: #374151;
                        overflow: hidden;
                    }

                    .load-title {
                        font-weight: 700;
                        color: #111827;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        white-space: nowrap;
                    }

                   .load-subtitle {
                        margin-top: 2px;
                        color: #6b7280;
                        line-height: 1.35;
                        white-space: normal;
                    }

                    .badges {
                        margin-top: 2px;
                        display: flex;
                        gap: 3px;
                        flex-wrap: wrap;
                    }

                    .badge {
                        display: inline-block;
                        border-radius: 99px;
                        padding: 1px 4px;
                        font-size: 7px;
                        background: #eef2ff;
                        color: #3730a3;
                        border: 1px solid #c7d2fe;
                    }

                    .warning {
                        color: #92400e;
                        font-size: 7px;
                        margin-top: 1px;
                    }

                    .bottom-buses {
                        margin-top: 12px;
                        page-break-inside: avoid;
                        break-inside: avoid;
                    }

                    .bottom-bus {
                        display: grid;
                        grid-template-columns: 24px 1fr auto;
                        align-items: center;
                        gap: 8px;
                        margin-bottom: 5px;
                        font-size: 9px;
                        color: #374151;
                    }

                    .bus-label {
                        font-weight: 900;
                    }

                    .bus-line {
                        height: 3px;
                        border-radius: 99px;
                    }

                    .bus-n .bus-line {
                        background: #0ea5e9;
                    }

                    .bus-pe .bus-line {
                        background: #65a30d;
                    }

                    .bus-note {
                        font-size: 8px;
                        color: #6b7280;
                        white-space: nowrap;
                    }

                    .legend {
                        margin-top: 8px;
                        padding-top: 7px;
                        border-top: 1px solid #e5e7eb;
                        font-size: 8px;
                        line-height: 1.35;
                        color: #4b5563;
                    }

                    .disclaimer {
                        margin-top: 7px;
                        padding-top: 7px;
                        border-top: 1px solid #e5e7eb;
                        font-size: 8px;
                        line-height: 1.35;
                        color: #6b7280;
                    }

                    .footer {
                        margin-top: 10px;
                        padding-top: 6px;
                        border-top: 1px solid #e5e7eb;
                        font-size: 8px;
                        color: #6b7280;
                        display: flex;
                        justify-content: space-between;
                    }
                </style>
            </head>
            <body>
                ${renderPages(diagram)}
            </body>
            </html>
        """.trimIndent()
    }

    private fun renderPages(diagram: SingleLineDiagram): String {
        val pageGroups = diagram.phaseSections
            .flatMap { section -> section.groups.map { group -> section.phase to group } }
            .chunked(GROUPS_PER_PAGE)

        if (pageGroups.isEmpty()) {
            return renderPage(
                diagram = diagram,
                pageNumber = 1,
                totalPages = 1,
                includeIncomerChain = true,
                phaseSections = diagram.phaseSections.map { it.copy(groups = emptyList()) },
                includeBusesLegendAndDisclaimer = true
            )
        }

        val totalPages = pageGroups.size

        return pageGroups.mapIndexed { index, pairs ->
            val groupedByPhase = pairs.groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )

            val pageSections = diagram.phaseSections.mapNotNull { section ->
                val groups = groupedByPhase[section.phase].orEmpty()
                if (groups.isEmpty()) null else section.copy(groups = groups)
            }

            renderPage(
                diagram = diagram,
                pageNumber = index + 1,
                totalPages = totalPages,
                includeIncomerChain = index == 0,
                phaseSections = pageSections,
                includeBusesLegendAndDisclaimer = index == totalPages - 1
            )
        }.joinToString(separator = "\n")
    }

    private fun renderPage(
        diagram: SingleLineDiagram,
        pageNumber: Int,
        totalPages: Int,
        includeIncomerChain: Boolean,
        phaseSections: List<SingleLinePhaseSection>,
        includeBusesLegendAndDisclaimer: Boolean
    ): String {
        return """
            <main class="page">
                ${renderHeader(diagram, pageNumber, totalPages)}

                ${
            if (includeIncomerChain) {
                renderIncomerChain(diagram)
            } else {
                renderContinuationChain()
            }
        }

                ${phaseSections.joinToString(separator = "\n") { renderPhaseSection(it) }}

                ${
            if (includeBusesLegendAndDisclaimer) {
                """
                    ${renderBottomBuses(diagram)}
                    ${renderLegend()}
                    ${renderDisclaimer()}
                """.trimIndent()
            } else {
                ""
            }
        }

                ${renderFooter(pageNumber, totalPages)}
            </main>
        """.trimIndent()
    }

    private fun renderHeader(
        diagram: SingleLineDiagram,
        pageNumber: Int,
        totalPages: Int
    ): String {
        return """
            <header class="header">
                <div>
                    <div class="title">Однолинейная схема</div>
                    <div class="subtitle">${diagram.projectName.escapeHtml()}</div>
                </div>
                <div class="source">
                    Экспликация → SingleLineDiagram → PDF<br/>
                    Страница $pageNumber из $totalPages
                </div>
            </header>
        """.trimIndent()
    }

    private fun renderIncomerChain(diagram: SingleLineDiagram): String {
        val input = diagram.input
        val protection = diagram.protectionBlocks
            .joinToString(separator = " · ") { it.toCompactLabel() }
            .ifBlank { "общая защита не указана" }

        return """
            <section class="incomer-chain">
                <div class="chain-node">
                    ${input.title.escapeHtml()}
                    <span class="chain-caption">ввод</span>
                </div>
                <div class="v-line"></div>
                <div class="chain-node">
                    ${input.incomerLabel.orDash().escapeHtml()}
                    <span class="chain-caption">вводной аппарат</span>
                </div>
                <div class="v-line"></div>
                <div class="chain-node chain-node-muted">
                    ${protection.escapeHtml()}
                    <span class="chain-caption">защита</span>
                </div>
                <div class="v-line"></div>
                <div class="split-label">распределение по фазным шинам</div>
            </section>
        """.trimIndent()
    }

    private fun renderContinuationChain(): String {
        return """
            <section class="incomer-chain">
                <div class="chain-node chain-node-muted">
                    Продолжение схемы
                    <span class="chain-caption">группы нагрузок</span>
                </div>
                <div class="v-line"></div>
            </section>
        """.trimIndent()
    }

    private fun renderPhaseSection(section: SingleLinePhaseSection): String {
        return """
            <section class="phase-section ${section.phase.cssClass()}">
                <div class="phase-marker">
                    <div class="phase-badge">${section.phase.displayLabel()}</div>
                    <div class="phase-bus"></div>
                </div>

                <div class="phase-content">
                    <div class="phase-summary">
                        ${section.phaseBus.label.escapeHtml()} · ${section.totalCurrentAmps.formatAmps()} · ${section.totalInstalledPowerWatts.formatKw()}
                    </div>

                    ${section.groups.joinToString(separator = "\n") { renderBranch(it) }}
                </div>
            </section>
        """.trimIndent()
    }

    private fun renderBranch(group: SingleLineGroupBlock): String {
        val devices = group.devices.map { it.name }

        val deviceText = if (devices.isEmpty()) {
            "нагрузки не указаны"
        } else {
            devices.joinToString(separator = ", ")
        }

        val roomText = group.roomNames.joinToString().ifBlank { "помещение не указано" }

        val badges = buildList {
            group.rcdLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
            group.leakageCurrentMilliAmps?.let { add("${it} мА") }
        }

        return """
            <article class="branch">
                <div class="tee"></div>
                <div class="device-node">QF${group.groupNumber}</div>
                <div class="small-node">${group.breakerLabel.orDash().escapeHtml()}</div>
                <div class="small-node">${group.cableLabel.orDash().escapeHtml()}</div>
                <div class="small-node">${group.calculatedCurrentAmps.formatAmps()}</div>
                <div class="load-node">
                    <div class="load-title">${group.groupName.escapeHtml()} · ${roomText.escapeHtml()}</div>
                    <div class="load-subtitle">${deviceText.escapeHtml()}</div>
                    ${renderBadges(badges)}
                    ${renderWarnings(group)}
                </div>
            </article>
        """.trimIndent()
    }

    private fun renderBadges(badges: List<String>): String {
        if (badges.isEmpty()) return ""

        return """
            <div class="badges">
                ${badges.joinToString(separator = "\n") { "<span class=\"badge\">${it.escapeHtml()}</span>" }}
            </div>
        """.trimIndent()
    }

    private fun renderWarnings(group: SingleLineGroupBlock): String {
        if (group.warnings.isEmpty()) return ""

        return """
            <div class="warning">
                ${group.warnings.joinToString(separator = " · ") { it.escapeHtml() }}
            </div>
        """.trimIndent()
    }

    private fun renderBottomBuses(diagram: SingleLineDiagram): String {
        return """
            <section class="bottom-buses">
                <div class="bottom-bus bus-n">
                    <div class="bus-label">${diagram.neutralBus.label.escapeHtml()}</div>
                    <div class="bus-line"></div>
                    <div class="bus-note">нейтральная шина</div>
                </div>
                <div class="bottom-bus bus-pe">
                    <div class="bus-label">${diagram.protectiveEarthBus.label.escapeHtml()}</div>
                    <div class="bus-line"></div>
                    <div class="bus-note">защитная шина</div>
                </div>
            </section>
        """.trimIndent()
    }

    private fun renderLegend(): String {
        return """
            <section class="legend">
                <b>Легенда:</b>
                QF — групповой автомат; A/B/C — фазные шины; 3P — трёхфазная линия;
                N — нейтральная шина; PE — защитная шина.
                Подключения групп к N/PE показаны условно, без перегруза схемы линиями.
            </section>
        """.trimIndent()
    }

    private fun renderDisclaimer(): String {
        return """
            <section class="disclaimer">
                $DISCLAIMER
            </section>
        """.trimIndent()
    }

    private fun renderFooter(pageNumber: Int, totalPages: Int): String {
        return """
            <footer class="footer">
                <span>ВольтХом · инженерная визуализация</span>
                <span>Страница $pageNumber из $totalPages</span>
            </footer>
        """.trimIndent()
    }

    private fun SingleLineProtectionBlock.toCompactLabel(): String {
        val current = nominalCurrentAmps.formatAmps()
        val leakage = leakageCurrentMilliAmps?.let { " / $it мА" }.orEmpty()
        val descriptionPart = description?.takeIf { it.isNotBlank() }

        return buildString {
            append(title)
            append(": ")
            append(descriptionPart ?: current)
            append(leakage)
        }
    }

    private fun Phase.displayLabel(): String {
        return when (this) {
            Phase.A -> "A"
            Phase.B -> "B"
            Phase.C -> "C"
            Phase.THREE_PHASE -> "3P"
        }
    }

    private fun Phase.cssClass(): String {
        return when (this) {
            Phase.A -> "phase-a"
            Phase.B -> "phase-b"
            Phase.C -> "phase-c"
            Phase.THREE_PHASE -> "phase-3p"
        }
    }

    private fun String?.orDash(): String {
        return this?.takeIf { it.isNotBlank() } ?: "—"
    }

    private fun Double?.formatKw(): String {
        return this?.let { "%.1f кВт".format(it / 1000.0) } ?: "—"
    }

    private fun Double?.formatAmps(): String {
        return this?.let { "%.2f А".format(Locale.US, it) } ?: "—"

    }

    private fun String.escapeHtml(): String {
        return this
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}