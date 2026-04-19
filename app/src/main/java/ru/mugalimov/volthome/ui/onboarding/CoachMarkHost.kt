package ru.mugalimov.volthome.ui.onboarding

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingDismissReason
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen

private const val HOST_TAG = "ONBOARD_HOST"

/**
 * Единый host overlay.
 *
 * Host:
 * - подписывается на activeHint из coordinator
 * - находит валидный anchor в registry
 * - игнорирует stale anchors
 * - при отсутствии валидного anchor использует centered fallback
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
    } else {
        val sameTagAnyScreen = anchors.firstOrNull { it.targetTag == hint.targetTag }
        Log.d(
            HOST_TAG,
            buildString {
                append("HOST_RENDER mode=FALLBACK_MISSING_ANCHOR")
                append(" hintId=").append(hint.hintId.name)
                append(" screen=").append(currentScreen.name)
                append(" targetTag=").append(hint.targetTag.rawTag)
                append(" anchorsCount=").append(anchors.size)
                append(" sameTagAnyScreen=").append(sameTagAnyScreen != null)
                append(" sameTagScreen=").append(sameTagAnyScreen?.screenId?.name ?: "null")
                append(" sameTagAttached=").append(sameTagAnyScreen?.isAttached ?: false)
            }
        )
    }

    CoachMarkOverlay(
        activeHint = hint,
        anchorBounds = resolvedAnchor?.bounds,
        onDismiss = {
            scope.launch {
                coordinator.dismiss(OnboardingDismissReason.USER_DISMISSED)
            }
        },
        modifier = modifier
    )
}