package ru.mugalimov.volthome.ui.paywall

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong
import ru.mugalimov.volthome.core.analytics.PaywallSource
import ru.mugalimov.volthome.domain.model.ProFeature

/**
 * Глобальная шина для запроса paywall из любого места (VM/UI).
 */
@Singleton
class PaywallBus @Inject constructor() {

    data class Request(
        val id: Long,
        val feature: ProFeature,
        val source: PaywallSource
    )

    private val nextRequestId = AtomicLong(0L)
    private val _events = MutableSharedFlow<Request>(extraBufferCapacity = 1)
    val events: SharedFlow<Request> = _events.asSharedFlow()

    fun request(
        feature: ProFeature,
        source: PaywallSource = feature.defaultPaywallSource()
    ) {
        _events.tryEmit(
            Request(
                id = nextRequestId.incrementAndGet(),
                feature = feature,
                source = source
            )
        )
    }
}

private fun ProFeature.defaultPaywallSource(): PaywallSource = when (this) {
    ProFeature.PROJECTS_LIMIT -> PaywallSource.PROJECTS_LIMIT
    ProFeature.PHASE_DND_TEASER -> PaywallSource.MANUAL_BOARD
    ProFeature.ADVANCED_DEVICE_EDITOR -> PaywallSource.DEVICE_EDITOR
    ProFeature.PRO_REPORT -> PaywallSource.PDF_REPORT
    ProFeature.SINGLE_LINE_DIAGRAM -> PaywallSource.SINGLE_LINE_DIAGRAM
    ProFeature.PANEL_VISUALIZATION -> PaywallSource.BOARD_PREVIEW
    ProFeature.CABLE_LINE_CALCULATION -> PaywallSource.CABLE_CALCULATION
    ProFeature.CALC_EXPLANATIONS,
    ProFeature.CALC_WARNINGS,
    ProFeature.DECISION_DETAILS -> PaywallSource.CALCULATION_DETAILS
}
