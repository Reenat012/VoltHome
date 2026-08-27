package ru.mugalimov.volthome.ui.onboarding

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingDismissReason
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen

private const val HOST_TAG = "ONBOARD_HOST"
private const val TARGET_WAIT_MILLIS = 1_500L
private const val LAYOUT_SETTLE_MILLIS = 350L

/**
 * Единый host overlay.
 *
 * Host:
 * - подписывается на activeHint из coordinator
 * - находит валидный anchor в registry
 * - игнорирует stale anchors
 * - ждёт появления anchor и отменяет показ при timeout
 *
 * Host НЕ:
 * - принимает решение о показе
 * - не вызывает tryShow()
 * - не вычисляет predicate
 */
@Composable
fun CoachMarkHost(
    coordinator: OnboardingCoordinator,
    anchorRegistry: UiAnchorRegistry,
    currentScreen: OnboardingScreen?,
    modifier: Modifier = Modifier
) {
    val activeHint by coordinator.activeHint.collectAsState()
    val anchors by anchorRegistry.anchors.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentScreen) {
        coordinator.onScreenChanged(currentScreen)
    }

    val hint = activeHint ?: return

    // Если текущий экран не совпадает с экраном active hint,
    // host ничего не рисует. Это защита от "призраков" на другом экране.
    if (currentScreen == null || hint.screen != currentScreen) {
        Log.d(
            HOST_TAG,
            "HOST_SKIP reason=SCREEN_MISMATCH hintId=${hint.hintId.name} hintScreen=${hint.screen.name} currentScreen=${currentScreen?.name ?: "null"}"
        )
        return
    }

    val resolvedAnchor = hint.targetTag?.let { targetTag ->
        anchors.firstOrNull { anchor ->
            anchor.targetTag == targetTag &&
                    anchor.screenId == currentScreen &&
                    anchor.isAttached
        }
    }

    var readyToRender by remember(hint.hintId, hint.activatedAtMillis) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        hint.hintId,
        hint.activatedAtMillis,
        currentScreen,
        resolvedAnchor
    ) {
        readyToRender = false

        if (hint.targetTag != null && resolvedAnchor == null) {
            delay(TARGET_WAIT_MILLIS)
            Log.d(
                HOST_TAG,
                "HOST_CANCEL reason=TARGET_TIMEOUT hintId=${hint.hintId.name} targetTag=${hint.targetTag.rawTag}"
            )
            coordinator.dismiss(OnboardingDismissReason.SYSTEM_CANCELLED)
            return@LaunchedEffect
        }

        // Даём навигации, LazyColumn и анимациям закончить первый layout.
        delay(LAYOUT_SETTLE_MILLIS)
        coordinator.markPresented(hint.hintId)
        readyToRender = true
    }

    if (!readyToRender) return

    if (hint.targetTag == null) {
        Log.d(
            HOST_TAG,
            "HOST_RENDER mode=FALLBACK_NO_TARGET hintId=${hint.hintId.name} screen=${currentScreen.name}"
        )
    } else if (resolvedAnchor != null) {
        Log.d(
            HOST_TAG,
            "HOST_RENDER mode=ANCHORED hintId=${hint.hintId.name} screen=${currentScreen.name} targetTag=${hint.targetTag.rawTag} bounds=${resolvedAnchor.bounds}"
        )
    }

    CoachMarkOverlay(
        activeHint = hint,
        anchorBounds = resolvedAnchor?.bounds,
        onConfirmed = {
            scope.launch {
                coordinator.dismiss(OnboardingDismissReason.USER_CONFIRMED)
            }
        },
        onDeferred = {
            scope.launch {
                coordinator.dismiss(OnboardingDismissReason.USER_DEFERRED)
            }
        },
        onSkipAll = {
            scope.launch {
                coordinator.disableAll()
            }
        },
        modifier = modifier
    )
}
