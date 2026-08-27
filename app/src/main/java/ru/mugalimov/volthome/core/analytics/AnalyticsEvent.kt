package ru.mugalimov.volthome.core.analytics

enum class AnalyticsMode(val value: String) {
    AUTOMATIC("automatic"),
    MANUAL("manual")
}

enum class PaywallSource(val value: String) {
    BOARD_PREVIEW("board_preview"),
    MANUAL_BOARD("manual_board"),
    PROJECTS_LIMIT("projects_limit"),
    DEVICE_EDITOR("device_editor"),
    PDF_REPORT("pdf_report"),
    SINGLE_LINE_DIAGRAM("single_line_diagram"),
    CALCULATION_DETAILS("calculation_details"),
    CABLE_CALCULATION("cable_calculation"),
    PRO_MENU("pro_menu"),
    REPORT_PREVIEW("report_preview"),
    PRO_SCREEN("pro_screen")
}

enum class BoardEntryPoint(val value: String) {
    AFTER_PURCHASE("after_purchase"),
    PROJECT("project"),
    AFTER_CALCULATION("after_calculation")
}

sealed interface AnalyticsEvent {
    data class ProjectCreated(
        val mode: AnalyticsMode
    ) : AnalyticsEvent

    data class CalculationCompleted(
        val linesCount: Int,
        val phaseCount: Int? = null
    ) : AnalyticsEvent

    data class BoardPreviewShown(
        val devicesCount: Int? = null,
        val dinRailsCount: Int? = null,
        val modulesUsed: Int? = null,
        val modulesTotal: Int? = null
    ) : AnalyticsEvent

    data class PaywallShown(
        val source: PaywallSource
    ) : AnalyticsEvent

    data class PurchaseStarted(
        val source: PaywallSource,
        val productId: String? = null
    ) : AnalyticsEvent

    data class PurchaseSuccess(
        val source: PaywallSource,
        val productId: String? = null
    ) : AnalyticsEvent

    data class BoardOpened(
        val mode: AnalyticsMode,
        val entryPoint: BoardEntryPoint? = null
    ) : AnalyticsEvent
}

data class AnalyticsReport(
    val name: String,
    val parameters: Map<String, Any>
)

fun AnalyticsEvent.toReport(): AnalyticsReport = when (this) {
    is AnalyticsEvent.ProjectCreated -> AnalyticsReport(
        name = "project_created",
        parameters = mapOf("mode" to mode.value)
    )

    is AnalyticsEvent.CalculationCompleted -> AnalyticsReport(
        name = "calculation_completed",
        parameters = buildMap {
            put("lines_count", linesCount)
            phaseCount?.let { put("phase_count", it) }
        }
    )

    is AnalyticsEvent.BoardPreviewShown -> AnalyticsReport(
        name = "board_preview_shown",
        parameters = buildMap {
            devicesCount?.let { put("devices_count", it) }
            dinRailsCount?.let { put("din_rails_count", it) }
            modulesUsed?.let { put("modules_used", it) }
            modulesTotal?.let { put("modules_total", it) }
        }
    )

    is AnalyticsEvent.PaywallShown -> AnalyticsReport(
        name = "paywall_shown",
        parameters = mapOf("source" to source.value)
    )

    is AnalyticsEvent.PurchaseStarted -> AnalyticsReport(
        name = "purchase_started",
        parameters = buildMap {
            put("source", source.value)
            productId?.takeIf(String::isNotBlank)?.let { put("product_id", it) }
        }
    )

    is AnalyticsEvent.PurchaseSuccess -> AnalyticsReport(
        name = "purchase_success",
        parameters = buildMap {
            put("source", source.value)
            productId?.takeIf(String::isNotBlank)?.let { put("product_id", it) }
        }
    )

    is AnalyticsEvent.BoardOpened -> AnalyticsReport(
        name = "board_opened",
        parameters = buildMap {
            put("mode", mode.value)
            entryPoint?.let { put("entry_point", it.value) }
        }
    )
}
