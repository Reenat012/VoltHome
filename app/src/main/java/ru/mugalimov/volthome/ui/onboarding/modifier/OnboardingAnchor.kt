package ru.mugalimov.volthome.ui.onboarding.modifier

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import ru.mugalimov.volthome.ui.onboarding.OnboardingRuntimeEntryPoint
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag

/**
 * Helper-модификатор для регистрации UI-anchor.
 *
 * Использует:
 * - boundsInRoot()
 * - screenId
 * - targetTag
 *
 * При уходе composable anchor помечается как detached.
 */
fun Modifier.onboardingAnchor(
    targetTag: OnboardingTargetTag,
    screenId: OnboardingScreen
): Modifier = composed {
    val context = LocalContext.current

    val registry = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OnboardingRuntimeEntryPoint::class.java
        ).uiAnchorRegistry()
    }

    DisposableEffect(registry, targetTag, screenId) {
        onDispose {
            // При уходе composable anchor инвалидируется.
            registry.markDetached(
                targetTag = targetTag,
                screenId = screenId
            )
        }
    }

    this.onGloballyPositioned { coords ->
        registry.updateAnchor(
            targetTag = targetTag,
            screenId = screenId,
            bounds = coords.boundsInRoot(),
            isAttached = coords.isAttached
        )
    }
}