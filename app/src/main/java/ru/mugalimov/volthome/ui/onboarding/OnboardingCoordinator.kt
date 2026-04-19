package ru.mugalimov.volthome.ui.onboarding

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mugalimov.volthome.data.repository.OnboardingRepository
import ru.mugalimov.volthome.ui.onboarding.model.ActiveHint
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingDismissReason
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen

/**
 * Единая точка истины для active hint.
 *
 * В commit 1 координатор умеет только:
 * - хранить максимум одну активную подсказку
 * - проверять shown flag
 * - проверять cooldown
 * - принимать dismiss
 * - писать persisted state
 *
 * Здесь специально нет:
 * - экранной интеграции
 * - overlay
 * - targetTag / anchors
 * - request-моделей
 */
@Singleton
class OnboardingCoordinator @Inject constructor(
    private val repository: OnboardingRepository
) {

    companion object {
        /**
         * Базовый cooldown между любыми подсказками.
         * Вынесен в константу commit 1, без дополнительной конфигурации.
         */
        const val DEFAULT_COOLDOWN_MILLIS: Long = 30_000L
    }

    private val mutex = Mutex()

    // Храним максимум одну активную подсказку.
    private val _activeHint = MutableStateFlow<ActiveHint?>(null)
    val activeHint: StateFlow<ActiveHint?> = _activeHint.asStateFlow()

    /**
     * Пытается активировать hint.
     *
     * Возвращает:
     * - true, если hint стал активным
     * - false, если hint отклонён по любому guard-условию
     */
    suspend fun tryShow(
        hintId: OnboardingHintId,
        screen: OnboardingScreen,
        nowMillis: Long = System.currentTimeMillis(),
        cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS
    ): Boolean = mutex.withLock {
        // Single-active invariant: пока один hint активен, второй не допускаем.
        if (_activeHint.value != null) return false

        // Если hint уже был показан и подтверждён persisted state, повтор не допускаем.
        if (repository.isShown(hintId)) return false

        // Глобальный cooldown между любыми hint.
        val lastAnyHintShownAt = repository.getLastAnyHintShownAt()
        val cooldownActive =
            lastAnyHintShownAt > 0L && (nowMillis - lastAnyHintShownAt) < cooldownMillis
        if (cooldownActive) return false

        // Активируем hint.
        _activeHint.value = ActiveHint(
            hintId = hintId,
            screen = screen,
            activatedAtMillis = nowMillis
        )

        // Фиксируем момент показа для глобального cooldown.
        repository.setLastAnyHintShownAt(nowMillis)

        true
    }

    /**
     * Закрывает текущий активный hint.
     *
     * По MVP-правилу commit 1 persisted shown-flag пишется только
     * для пользовательских сценариев dismiss/skip.
     */
    suspend fun dismiss(reason: OnboardingDismissReason) = mutex.withLock {
        val current = _activeHint.value ?: return

        if (reason.persistShownFlag) {
            repository.markShown(current.hintId)
        }

        _activeHint.value = null
    }
}