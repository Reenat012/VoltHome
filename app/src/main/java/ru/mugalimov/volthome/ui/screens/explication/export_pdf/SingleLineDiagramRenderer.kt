package ru.mugalimov.volthome.ui.screens.explication.export_pdf

import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.singleline.SingleLineDiagram
import ru.mugalimov.volthome.domain.model.singleline.SingleLineGroupBlock
import ru.mugalimov.volthome.domain.model.singleline.SingleLinePhaseSection
import ru.mugalimov.volthome.domain.model.singleline.SingleLineProtectionBlock
import ru.mugalimov.volthome.domain.model.singleline.SingleLineProtectionType
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
    private const val GROUPS_PER_PAGE = 6

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
                        margin: 0 0 12px;
                        page-break-inside: avoid;
                        break-inside: avoid;
                    }

                    .supply-label {
                        font-size: 8px;
                        font-weight: 800;
                        letter-spacing: .08em;
                        text-transform: uppercase;
                        color: #4b5563;
                    }

                    .supply-arrow {
                        width: 0;
                        height: 0;
                        border-left: 4px solid transparent;
                        border-right: 4px solid transparent;
                        border-top: 7px solid #111827;
                        margin-top: 3px;
                    }

                    .vertical-conductor {
                        width: 2px;
                        height: 9px;
                        background: #111827;
                    }

                    .incomer-device {
                        display: grid;
                        grid-template-columns: 52px 118px;
                        align-items: center;
                        column-gap: 9px;
                    }

                    .incomer-device-copy {
                        text-align: left;
                        line-height: 1.25;
                    }

                    .apparatus-ref {
                        font-size: 8px;
                        font-weight: 900;
                        color: #111827;
                    }

                    .apparatus-value {
                        margin-top: 1px;
                        font-size: 8px;
                        color: #374151;
                    }

                    .device-symbol {
                        display: block;
                        width: 52px;
                        height: 38px;
                    }

                    .distribution-trunk {
                        width: 2px;
                        height: 10px;
                        background: #111827;
                    }

                    .distribution-bar {
                        position: relative;
                        width: 68%;
                        height: 2px;
                        background: #111827;
                    }

                    .distribution-bar::before {
                        content: "";
                        position: absolute;
                        left: 50%;
                        top: -3px;
                        width: 7px;
                        height: 7px;
                        margin-left: -3px;
                        border-radius: 50%;
                        background: #111827;
                    }

                    .distribution-caption {
                        margin-top: 4px;
                        font-size: 7px;
                        letter-spacing: .04em;
                        text-transform: uppercase;
                        color: #6b7280;
                    }

                    .phase-section {
                        margin-bottom: 13px;
                        page-break-inside: avoid;
                        break-inside: avoid;
                    }

                    .phase-heading {
                        display: flex;
                        align-items: baseline;
                        justify-content: space-between;
                        margin-bottom: 4px;
                    }

                    .phase-name {
                        display: flex;
                        align-items: center;
                        gap: 6px;
                        font-size: 9px;
                        font-weight: 900;
                    }

                    .phase-badge {
                        display: inline-flex;
                        width: 22px;
                        height: 18px;
                        border: 1px solid #9ca3af;
                        border-radius: 4px;
                        align-items: center;
                        justify-content: center;
                        font-size: 9px;
                        font-weight: 900;
                    }

                    .phase-summary {
                        font-size: 7px;
                        color: #6b7280;
                    }

                    .phase-bus-track {
                        position: relative;
                        height: 6px;
                    }

                    .phase-bus-line {
                        position: absolute;
                        left: 11px;
                        right: 11px;
                        top: 2px;
                        height: 3px;
                    }

                    .phase-a .phase-badge { background: #fff7cc; }
                    .phase-a .phase-bus-line { background: #eab308; }

                    .phase-b .phase-badge { background: #dcfce7; }
                    .phase-b .phase-bus-line { background: #16a34a; }

                    .phase-c .phase-badge { background: #ffe4e6; }
                    .phase-c .phase-bus-line { background: #e11d48; }

                    .phase-3p .phase-badge { background: #f3f4f6; }
                    .phase-3p .phase-bus-line { background: #6b7280; }

                    .feeders {
                        display: grid;
                        grid-template-columns: repeat(3, minmax(0, 1fr));
                        column-gap: 9px;
                        row-gap: 10px;
                    }

                    .feeder-row + .feeder-row {
                        margin-top: 8px;
                    }

                    .feeder {
                        position: relative;
                        min-width: 0;
                        text-align: center;
                        page-break-inside: avoid;
                        break-inside: avoid;
                    }

                    .feeder-drop {
                        position: relative;
                        width: 2px;
                        height: 10px;
                        margin: 0 auto;
                        background: #111827;
                    }

                    .feeder-drop::before {
                        content: "";
                        position: absolute;
                        left: -3px;
                        top: -5px;
                        width: 8px;
                        height: 8px;
                        border-radius: 50%;
                        background: #111827;
                    }

                    .feeder-device {
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 38px;
                    }

                    .feeder-device-copy {
                        width: 58px;
                        margin-left: 3px;
                        text-align: left;
                        line-height: 1.15;
                    }

                    .device-connector {
                        width: 2px;
                        height: 5px;
                        margin: 0 auto;
                        background: #111827;
                    }

                    .cable-mark {
                        display: inline-block;
                        padding: 2px 4px;
                        border-top: 1px solid #9ca3af;
                        border-bottom: 1px solid #9ca3af;
                        font-size: 7px;
                        color: #374151;
                        white-space: nowrap;
                    }

                    .load-arrow {
                        width: 0;
                        height: 0;
                        margin: 2px auto 3px;
                        border-left: 4px solid transparent;
                        border-right: 4px solid transparent;
                        border-top: 7px solid #111827;
                    }

                    .load-terminal {
                        padding-top: 3px;
                        border-top: 1px solid #d1d5db;
                        font-size: 7px;
                        color: #374151;
                        overflow: hidden;
                    }

                    .load-title {
                        font-size: 8px;
                        font-weight: 800;
                        color: #111827;
                        line-height: 1.2;
                    }

                    .load-subtitle {
                        margin-top: 2px;
                        color: #6b7280;
                        line-height: 1.25;
                    }

                    .load-meta {
                        margin-top: 2px;
                        font-size: 7px;
                        color: #4b5563;
                    }

                    .warning {
                        color: #92400e;
                        font-size: 6px;
                        margin-top: 2px;
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
                renderIncomerChain(diagram) + renderUnassignedApparatus(diagram)
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
                    ВольтХом · автоматическая схема<br/>
                    Страница $pageNumber из $totalPages
                </div>
            </header>
        """.trimIndent()
    }

    private fun renderIncomerChain(diagram: SingleLineDiagram): String {
        val input = diagram.input
        val protection = if (diagram.protectionBlocks.isEmpty()) {
            renderIncomerDevice(
                reference = "QF0",
                title = "Вводной аппарат",
                value = input.incomerLabel.orDash(),
                isRcd = false
            )
        } else {
            diagram.protectionBlocks.joinToString(separator = "\n") { block ->
                renderIncomerDevice(
                    reference = block.referenceLabel(),
                    title = block.title,
                    value = block.toCompactLabel(),
                    isRcd = block.type == SingleLineProtectionType.RCD ||
                            block.type == SingleLineProtectionType.DIFF_BREAKER ||
                            block.leakageCurrentMilliAmps != null
                )
            }
        }

        val totals = listOfNotNull(
            input.totalCalculatedPowerWatts?.formatKw(),
            input.totalCurrentAmps?.formatAmps()
        ).joinToString(separator = " · ")

        return """
            <section class="incomer-chain">
                <div class="supply-label">Сеть · ${input.title.escapeHtml()}</div>
                <div class="supply-arrow"></div>
                <div class="vertical-conductor"></div>
                $protection
                <div class="distribution-trunk"></div>
                <div class="distribution-bar"></div>
                <div class="distribution-caption">
                    Распределительная шина${if (totals.isBlank()) "" else " · ${totals.escapeHtml()}"}
                </div>
            </section>
        """.trimIndent()
    }

    private fun renderContinuationChain(): String {
        return """
            <section class="incomer-chain">
                <div class="supply-label">Продолжение распределительной схемы</div>
                <div class="supply-arrow"></div>
                <div class="distribution-trunk"></div>
                <div class="distribution-bar"></div>
            </section>
        """.trimIndent()
    }

    private fun renderPhaseSection(section: SingleLinePhaseSection): String {
        val feederRows = section.groups
            .chunked(3)
            .joinToString(separator = "\n") { groups ->
                """
                    <div class="feeder-row">
                        <div class="phase-bus-track">
                            <div class="phase-bus-line"></div>
                        </div>
                        <div class="feeders">
                            ${groups.joinToString(separator = "\n") { renderBranch(it) }}
                        </div>
                    </div>
                """.trimIndent()
            }

        return """
            <section class="phase-section ${section.phase.cssClass()}">
                <div class="phase-heading">
                    <div class="phase-name">
                        <span class="phase-badge">${section.phase.displayLabel()}</span>
                        ${section.phaseBus.label.escapeHtml()}
                    </div>
                    <div class="phase-summary">
                        ${section.totalCurrentAmps.formatAmps()} · ${section.totalInstalledPowerWatts.formatKw()}
                    </div>
                </div>
                $feederRows
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

        val auxiliaryDevices = group.auxiliaryProtectionBlocks.joinToString(separator = "\n") { block ->
            """
                ${renderFeederDevice(
                    reference = block.referenceLabel(),
                    value = block.toCompactLabel(),
                    isRcd = false
                )}
                <div class="device-connector"></div>
            """.trimIndent()
        }

        val rcdDevice = group.rcdLabel
            ?.takeIf { it.isNotBlank() }
            ?.let { label ->
                """
                    ${renderFeederDevice(
                    reference = "QD${group.groupNumber}",
                    value = label,
                    isRcd = true
                )}
                    <div class="device-connector"></div>
                """.trimIndent()
            }
            .orEmpty()

        return """
            <article class="feeder">
                <div class="feeder-drop"></div>
                $auxiliaryDevices
                $rcdDevice
                ${renderFeederDevice(
            reference = "QF${group.groupNumber}",
            value = group.breakerLabel.orDash(),
            isRcd = false
        )}
                <div class="device-connector"></div>
                <div class="cable-mark">${group.cableLabel.orDash().escapeHtml()}</div>
                <div class="device-connector"></div>
                <div class="load-arrow"></div>
                <div class="load-terminal">
                    <div class="load-title">Гр. ${group.groupNumber} · ${group.groupName.toDisplayGroupName().escapeHtml()}</div>
                    <div class="load-meta">${roomText.escapeHtml()} · ${group.calculatedCurrentAmps.formatAmps()}</div>
                    <div class="load-subtitle">${deviceText.escapeHtml()}</div>
                    ${renderWarnings(group)}
                </div>
            </article>
        """.trimIndent()
    }

    private fun renderUnassignedApparatus(diagram: SingleLineDiagram): String {
        if (diagram.unassignedProtectionBlocks.isEmpty()) return ""
        val lines = diagram.unassignedProtectionBlocks.joinToString("<br/>") { block ->
            "• ${block.referenceLabel().escapeHtml()} · ${block.title.escapeHtml()} — " +
                "${block.warning.orDash().escapeHtml()}"
        }
        return """
            <section class="legend" style="color:#92400e;border:1px solid #f59e0b;padding:7px;">
                <b>Ручные аппараты без подключения</b><br/>
                $lines<br/>
                Аппараты сохранены в компоновке и смете, но не включены в электрическую цепь.
            </section>
        """.trimIndent()
    }

    private fun renderIncomerDevice(
        reference: String,
        title: String,
        value: String,
        isRcd: Boolean
    ): String {
        return """
            <div class="incomer-device">
                ${renderApparatusSymbol(isRcd)}
                <div class="incomer-device-copy">
                    <div class="apparatus-ref">${reference.escapeHtml()} · ${title.escapeHtml()}</div>
                    <div class="apparatus-value">${value.escapeHtml()}</div>
                </div>
            </div>
        """.trimIndent()
    }

    private fun renderFeederDevice(
        reference: String,
        value: String,
        isRcd: Boolean
    ): String {
        return """
            <div class="feeder-device">
                ${renderApparatusSymbol(isRcd)}
                <div class="feeder-device-copy">
                    <div class="apparatus-ref">${reference.escapeHtml()}</div>
                    <div class="apparatus-value">${value.escapeHtml()}</div>
                </div>
            </div>
        """.trimIndent()
    }

    private fun renderApparatusSymbol(isRcd: Boolean): String {
        val rcdMark = if (isRcd) {
            """
                <path d="M14 29 L18 22 L22 29 Z" fill="none" stroke="#111827" stroke-width="1.2"/>
                <text x="30" y="31" font-size="7" font-family="sans-serif" fill="#111827">Δ</text>
            """.trimIndent()
        } else {
            ""
        }

        return """
            <svg class="device-symbol" viewBox="0 0 52 38" aria-hidden="true">
                <line x1="26" y1="0" x2="26" y2="5" stroke="#111827" stroke-width="1.6"/>
                <rect x="10" y="5" width="32" height="28" rx="1" fill="white" stroke="#111827" stroke-width="1.4"/>
                <circle cx="26" cy="11" r="1.8" fill="#111827"/>
                <circle cx="26" cy="27" r="1.8" fill="#111827"/>
                <line x1="24" y1="25" x2="32" y2="13" stroke="#111827" stroke-width="1.6" stroke-linecap="round"/>
                $rcdMark
                <line x1="26" y1="33" x2="26" y2="38" stroke="#111827" stroke-width="1.6"/>
            </svg>
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
                QF — автоматический выключатель; QD — устройство дифференциальной защиты;
                A/B/C — фазные шины; 3P — трёхфазная линия;
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
        val descriptionPart = description?.takeIf { it.isNotBlank() }
        val baseLabel = descriptionPart ?: current
        val leakage = leakageCurrentMilliAmps
            ?.takeUnless { baseLabel.contains("мА", ignoreCase = true) }
            ?.let { " / $it мА" }
            .orEmpty()

        return "$baseLabel$leakage"
    }

    private fun SingleLineProtectionBlock.referenceLabel(): String {
        return when (type) {
            SingleLineProtectionType.INPUT_BREAKER -> "QF0"
            SingleLineProtectionType.VOLTAGE_RELAY -> id.ifBlank { "KV0" }
            SingleLineProtectionType.PHASE_CONTROL_RELAY -> id.ifBlank { "KF0" }
            SingleLineProtectionType.CURRENT_RELAY -> id.ifBlank { "KA0" }
            SingleLineProtectionType.MODULAR_CONTACTOR -> id.ifBlank { "KM0" }
            SingleLineProtectionType.SURGE_PROTECTION -> id.ifBlank { "FV0" }
            SingleLineProtectionType.RCD -> "QD0"
            SingleLineProtectionType.DIFF_BREAKER -> "QFD0"
            SingleLineProtectionType.GROUP_BREAKER -> "QF"
            SingleLineProtectionType.UNKNOWN -> "Q0"
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

    private fun String.toDisplayGroupName(): String {
        return when (uppercase(Locale.ROOT)) {
            "LIGHTING" -> "Освещение"
            "SOCKET" -> "Розеточная линия"
            "HEAVY_DUTY" -> "Выделенная линия"
            else -> replace('_', ' ')
        }
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
