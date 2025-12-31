package ru.mugalimov.volthome.ui.utilities

import androidx.compose.foundation.relocation.BringIntoViewRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val BRING_INTO_VIEW_DELAY_MS = 200L

/**
 * Единый helper для bringIntoView при получении фокуса.
 *
 * Использование:
 * onFocusChanged {
 *   bringIntoViewOnFocus(scope, bringIntoViewRequester, it.isFocused)
 * }
 */
fun bringIntoViewOnFocus(
    scope: CoroutineScope,
    requester: BringIntoViewRequester,
    focused: Boolean
) {
    if (!focused) return

    scope.launch {
        delay(BRING_INTO_VIEW_DELAY_MS)
        requester.bringIntoView()
    }
}