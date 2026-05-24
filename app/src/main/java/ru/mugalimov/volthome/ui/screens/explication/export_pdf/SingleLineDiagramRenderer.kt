package ru.mugalimov.volthome.ui.screens.explication.export_pdf

import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.singleline.SingleLineDiagram
import ru.mugalimov.volthome.domain.model.singleline.SingleLineGroupBlock
import ru.mugalimov.volthome.domain.model.singleline.SingleLinePhaseSection
import ru.mugalimov.volthome.domain.model.singleline.SingleLineProtectionBlock

/**
 * HTML-renderer однолинейной схемы.
 *
 * Важно:
 * - renderer не рассчитывает токи, мощности, автоматы и кабели;
 * - renderer только отображает уже готовую модель SingleLineDiagram;
 * - layout рассчитан под A4 vertical;
 * - полноценная pagination будет отдельным коммитом.
 */
object SingleLineDiagramRenderer {

    private const val DISCLAIMER =
        "Однолинейная схема сформирована автоматически на основе данных, введённых пользователем в приложении ВольтХом. " +
                "Материал является инженерной визуализацией и не заменяет проектную документацию, разработанную уполномоченным специалистом."

    fun render(
        diagram: SingleLineDiagram
    ): String {
        return """
            <!doctype html>
            <html lang="ru">
            <head>
                <meta charset="utf-8" />
                <style>
                    @page {
                        size: A4 portrait;
                        margin: 14mm;
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

                    .header {
                        border-bottom: 1px solid #d1d5db;
                        padding-bottom: 10px;
                        margin-bottom: 14px;
                    }

                    .title {
                        font-size: 22px;
                        font-weight: 700;
                        margin-bottom: 4px;
                    }

                    .subtitle {
                        font-size: 12px;
                        color: #4b5563;
                    }

                    .meta {
                        margin-top: 8px;
                        font-size: 10px;
                        color: #6b7280;
                    }

                    .scheme {
                        display: block;
                    }

                    .block {
                        border: 1px solid #d1d5db;
                        border-radius: 10px;
                        padding: 10px;
                        margin-bottom: 10px;
                        page-break-inside: avoid;
                    }

                    .block-title {
                        font-size: 14px;
                        font-weight: 700;
                        margin-bottom: 6px;
                    }

                    .block-row {
                        font-size: 11px;
                        line-height: 1.45;
                        color: #374151;
                    }

                    .arrow {
                        text-align: center;
                        font-size: 16px;
                        color: #6b7280;
                        margin: 2px 0 8px 0;
                    }

                    .phase-section {
                        border: 1px solid #e5e7eb;
                        border-radius: 12px;
                        padding: 10px;
                        margin-bottom: 12px;
                        page-break-inside: avoid;
                    }

                    .phase-a {
                        background: #fff8db;
                    }

                    .phase-b {
                        background: #e8f7f4;
                    }

                    .phase-c {
                        background: #ffe8ec;
                    }

                    .phase-other {
                        background: #f3f4f6;
                    }

                    .phase-title {
                        font-size: 15px;
                        font-weight: 700;
                        margin-bottom: 8px;
                    }

                    .group {
                        background: #ffffff;
                        border: 1px solid #d1d5db;
                        border-radius: 10px;
                        padding: 9px;
                        margin-bottom: 8px;
                        page-break-inside: avoid;
                    }

                    .group-title {
                        font-size: 13px;
                        font-weight: 700;
                        margin-bottom: 5px;
                    }

                    .group-grid {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 4px 10px;
                        margin-bottom: 6px;
                    }

                    .label {
                        font-size: 10px;
                        color: #6b7280;
                    }

                    .value {
                        font-size: 11px;
                        color: #111827;
                    }

                    .devices {
                        margin-top: 6px;
                        font-size: 10px;
                        color: #374151;
                    }

                    .warning {
                        margin-top: 5px;
                        font-size: 10px;
                        color: #92400e;
                    }

                    .buses {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 10px;
                        margin-top: 12px;
                    }

                    .bus {
                        border: 1px solid #d1d5db;
                        border-radius: 10px;
                        padding: 9px;
                        font-size: 12px;
                        font-weight: 700;
                    }

                    .bus-n {
                        background: #e0f2fe;
                    }

                    .bus-pe {
                        background: #ecfccb;
                    }

                    .legend {
                        margin-top: 14px;
                        border-top: 1px solid #e5e7eb;
                        padding-top: 10px;
                        font-size: 10px;
                        color: #4b5563;
                    }

                    .disclaimer {
                        margin-top: 12px;
                        border-top: 1px solid #e5e7eb;
                        padding-top: 10px;
                        font-size: 9px;
                        line-height: 1.45;
                        color: #6b7280;
                    }
                </style>
            </head>
            <body>
                <main class="page">
                    ${renderHeader(diagram)}
                    <section class="scheme">
                        ${renderInput(diagram)}
                        <div class="arrow">↓</div>
                        ${renderProtectionBlocks(diagram.protectionBlocks)}
                        <div class="arrow">↓</div>
                        ${
            diagram.phaseSections.joinToString(separator = "\n") {
                renderPhaseSection(
                    it
                )
            }
        }
                        ${renderBuses(diagram)}
                        ${renderLegend()}
                        ${renderDisclaimer()}
                    </section>
                </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun renderHeader(
        diagram: SingleLineDiagram
    ): String {
        return """
            <header class="header">
                <div class="title">Однолинейная схема</div>
                <div class="subtitle">${diagram.projectName.escapeHtml()}</div>
                <div class="meta">Источник данных: текущая экспликация проекта</div>
            </header>
        """.trimIndent()
    }

    private fun renderInput(
        diagram: SingleLineDiagram
    ): String {
        val input = diagram.input

        return """
            <section class="block">
                <div class="block-title">${input.title.escapeHtml()}</div>
                <div class="block-row">Вводной аппарат: ${
            input.incomerLabel.orDash().escapeHtml()
        }</div>
                <div class="block-row">Номинал ввода: ${
            input.incomerNominalCurrentLabel.orDash().escapeHtml()
        }</div>
                <div class="block-row">Установленная мощность: ${input.totalInstalledPowerWatts.formatWatts()}</div>
                <div class="block-row">Расчётная мощность: ${input.totalCalculatedPowerWatts.formatWatts()}</div>
                <div class="block-row">Расчётный ток: ${input.totalCurrentAmps.formatAmps()}</div>
            </section>
        """.trimIndent()
    }

    private fun renderProtectionBlocks(
        blocks: List<SingleLineProtectionBlock>
    ): String {
        if (blocks.isEmpty()) {
            return """
                <section class="block">
                    <div class="block-title">Общая защита</div>
                    <div class="block-row">Данные по общей защите отсутствуют в экспликации</div>
                </section>
            """.trimIndent()
        }

        return blocks.joinToString(separator = "\n") { block ->
            """
                <section class="block">
                    <div class="block-title">${block.title.escapeHtml()}</div>
                    <div class="block-row">Тип: ${block.type.name.escapeHtml()}</div>
                    <div class="block-row">Фаза: ${block.phase?.name ?: "—"}</div>
                    <div class="block-row">Номинал: ${block.nominalCurrentAmps.formatAmps()}</div>
                    <div class="block-row">Ток утечки: ${block.leakageCurrentMilliAmps.formatMilliAmps()}</div>
                    <div class="block-row">Описание: ${
                block.description.orDash().escapeHtml()
            }</div>
                </section>
            """.trimIndent()
        }
    }

    private fun renderPhaseSection(
        section: SingleLinePhaseSection
    ): String {
        return """
            <section class="phase-section ${section.phase.cssClass()}">
                <div class="phase-title">Фаза ${section.phase.name}</div>
                <div class="block-row">Фазная шина: ${section.phaseBus.label.escapeHtml()}</div>
                <div class="block-row">Ток секции: ${section.totalCurrentAmps.formatAmps()}</div>
                <div class="block-row">Установленная мощность: ${section.totalInstalledPowerWatts.formatWatts()}</div>
                <div class="block-row">Расчётная мощность: ${section.totalCalculatedPowerWatts.formatWatts()}</div>
                <div class="arrow">↓</div>
                ${section.groups.joinToString(separator = "\n") { renderGroup(it) }}
            </section>
        """.trimIndent()
    }

    private fun renderGroup(
        group: SingleLineGroupBlock
    ): String {
        return """
            <article class="group">
                <div class="group-title">Группа ${group.groupNumber}: ${group.groupName.escapeHtml()}</div>

                <div class="group-grid">
                    ${renderParam("Фаза", group.phase?.name ?: "—")}
                    ${renderParam("Автомат", group.breakerLabel.orDash())}
                    ${renderParam("Кабель", group.cableLabel.orDash())}
                    ${renderParam("УЗО/дифзащита", group.rcdLabel.orDash())}
                    ${renderParam("Ток утечки", group.leakageCurrentMilliAmps.formatMilliAmps())}
                    ${renderParam("Расчётный ток", group.calculatedCurrentAmps.formatAmps())}
                    ${
            renderParam(
                "Установленная мощность",
                group.installedPowerWatts.formatWatts()
            )
        }
                    ${renderParam("Расчётная мощность", group.calculatedPowerWatts.formatWatts())}
                </div>

                <div class="devices">
                    <b>Помещения:</b> ${
            group.roomNames.joinToString().ifBlank { "—" }.escapeHtml()
        }<br/>
                    <b>Нагрузки:</b> ${
            group.devices.joinToString { it.name }.ifBlank { "—" }.escapeHtml()
        }
                </div>

                ${renderWarnings(group)}
            </article>
        """.trimIndent()
    }

    private fun renderParam(
        label: String,
        value: String
    ): String {
        return """
            <div>
                <div class="label">${label.escapeHtml()}</div>
                <div class="value">${value.escapeHtml()}</div>
            </div>
        """.trimIndent()
    }

    private fun renderWarnings(
        group: SingleLineGroupBlock
    ): String {
        if (group.warnings.isEmpty()) return ""

        return """
            <div class="warning">
                ${group.warnings.joinToString(separator = "<br/>") { it.escapeHtml() }}
            </div>
        """.trimIndent()
    }

    private fun renderBuses(
        diagram: SingleLineDiagram
    ): String {
        return """
            <section class="buses">
                <div class="bus bus-n">${diagram.neutralBus.label.escapeHtml()}-шина</div>
                <div class="bus bus-pe">${diagram.protectiveEarthBus.label.escapeHtml()}-шина</div>
            </section>
        """.trimIndent()
    }

    private fun renderLegend(): String {
        return """
            <section class="legend">
                <b>Легенда:</b>
                A/B/C — фазные секции; N — нейтральная шина; PE — защитная шина.
                Подключения к N/PE показаны условно, без перегруза схемы линиями.
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

    private fun Phase.cssClass(): String {
        return when (this) {
            Phase.A -> "phase-a"
            Phase.B -> "phase-b"
            Phase.C -> "phase-c"
            Phase.THREE_PHASE -> "phase-other"
        }
    }

    private fun String?.orDash(): String {
        return this?.takeIf { it.isNotBlank() } ?: "—"
    }

    private fun Double?.formatWatts(): String {
        return this?.let { "${it.toInt()} Вт" } ?: "—"
    }

    private fun Double?.formatAmps(): String {
        return this?.let { "${roundOneDecimal(it)} А" } ?: "—"
    }

    private fun Int?.formatMilliAmps(): String {
        return this?.let { "$it мА" } ?: "—"
    }

    private fun roundOneDecimal(
        value: Double
    ): String {
        return kotlin.math.round(value * 10.0).div(10.0).toString()
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