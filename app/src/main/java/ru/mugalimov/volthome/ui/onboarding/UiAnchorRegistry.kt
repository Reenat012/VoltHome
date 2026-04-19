package ru.mugalimov.volthome.ui.onboarding

import androidx.compose.ui.geometry.Rect
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.model.UiAnchor

/**
 * Runtime-реестр target anchors.
 *
 * Обязательные инварианты:
 * - anchor хранит targetTag
 * - anchor хранит screenId
 * - anchor хранит bounds
 * - anchor хранит isAttached
 *
 * Поведение:
 * - stale anchor должен быть проигнорирован host-ом
 * - detached anchor не используется
 * - anchor другого экрана не используется
 */
@Singleton
class UiAnchorRegistry @Inject constructor() {

    private val backingMap = linkedMapOf<String, UiAnchor>()

    private val _anchors = MutableStateFlow<List<UiAnchor>>(emptyList())
    val anchors: StateFlow<List<UiAnchor>> = _anchors.asStateFlow()

    /**
     * Регистрирует или обновляет anchor.
     */
    fun updateAnchor(
        targetTag: OnboardingTargetTag,
        screenId: OnboardingScreen,
        bounds: Rect,
        isAttached: Boolean
    ) {
        val key = buildKey(targetTag, screenId)
        backingMap[key] = UiAnchor(
            targetTag = targetTag,
            screenId = screenId,
            bounds = bounds,
            isAttached = isAttached
        )
        publish()
    }

    /**
     * Помечает anchor как detached.
     *
     * Важно:
     * - мы не используем старые координаты после detachment
     * - host обязан игнорировать такой anchor
     */
    fun markDetached(
        targetTag: OnboardingTargetTag,
        screenId: OnboardingScreen
    ) {
        val key = buildKey(targetTag, screenId)
        val current = backingMap[key] ?: return

        backingMap[key] = current.copy(isAttached = false)
        publish()
    }

    /**
     * Полное удаление anchor при необходимости.
     */
    fun removeAnchor(
        targetTag: OnboardingTargetTag,
        screenId: OnboardingScreen
    ) {
        val key = buildKey(targetTag, screenId)
        backingMap.remove(key)
        publish()
    }

    private fun publish() {
        _anchors.value = backingMap.values.toList()
    }

    private fun buildKey(
        targetTag: OnboardingTargetTag,
        screenId: OnboardingScreen
    ): String {
        return "${screenId.name}:${targetTag.rawTag}"
    }
}