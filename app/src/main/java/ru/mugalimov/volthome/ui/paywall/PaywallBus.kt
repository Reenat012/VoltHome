package ru.mugalimov.volthome.ui.paywall

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.mugalimov.volthome.domain.model.ProFeature

/**
 * Глобальная шина для запроса paywall из любого места (VM/UI).
 */
@Singleton
class PaywallBus @Inject constructor() {

    private val _events = MutableSharedFlow<ProFeature>(extraBufferCapacity = 1)
    val events: SharedFlow<ProFeature> = _events.asSharedFlow()

    fun request(feature: ProFeature) {
        _events.tryEmit(feature)
    }
}