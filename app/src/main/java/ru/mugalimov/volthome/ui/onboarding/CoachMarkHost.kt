package ru.mugalimov.volthome.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingDismissReason
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen

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
    if (currentScreen == null || hint.screen != currentScreen) return

    val resolvedAnchor = hint.targetTag?.let { targetTag ->
        anchors.firstOrNull { anchor ->
            anchor.targetTag == targetTag &&
                    anchor.screenId == currentScreen &&
                    anchor.isAttached
        }
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