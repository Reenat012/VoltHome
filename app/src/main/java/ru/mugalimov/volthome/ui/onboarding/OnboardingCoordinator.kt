package ru.mugalimov.volthome.ui.onboarding

import android.util.Log
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
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag

/**
 * Единая точка истины для active hint.
 *
 * Commit 4:
 * - принимает пользовательский текст title/body
 * - по-прежнему не знает ничего о predicate или UI orchestration
 */
@Singleton
class OnboardingCoordinator @Inject constructor(
    private val repository: OnboardingRepository
) {

    companion object {
        /**
         * Для base hints 30 секунд — слишком жирно.
         * Пользователь успевает пройти Rooms -> Projects -> Loads,
         * а увидит только первую подсказку.
         *
         * Поэтому уменьшаем cooldown до 5 секунд.
         */
        const val DEFAULT_COOLDOWN_MILLIS: Long = 3_000L

        // Единый тег логов coordinator-а.
        private const val TAG = "ONBOARD_COORD"
    }

    private val mutex = Mutex()

    // Храним максимум одну активную подсказку.
    private val _activeHint = MutableStateFlow<ActiveHint?>(null)
    val activeHint: StateFlow<ActiveHint?> = _activeHint.asStateFlow()

    /**
     * Пытается активировать hint.
     *
     * targetTag может быть null:
     * - в этом случае host использует centered fallback
     */
    suspend fun tryShow(
        hintId: OnboardingHintId,
        screen: OnboardingScreen,
        targetTag: OnboardingTargetTag? = null,
        title: String,
        body: String,
        nowMillis: Long = System.currentTimeMillis(),
        cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS
    ): Boolean = mutex.withLock {
        val hasActiveHint = (_activeHint.value != null)

        // Сначала читаем persisted state и cooldown для прозрачной диагностики.
        val alreadyShown = repository.isShown(hintId)
        val lastAnyHintShownAt = repository.getLastAnyHintShownAt()
        val cooldownActive =
            lastAnyHintShownAt > 0L && (nowMillis - lastAnyHintShownAt) < cooldownMillis

        Log.d(
            TAG,
            buildString {
                append("TRY_SHOW")
                append(" hintId=").append(hintId.name)
                append(" screen=").append(screen.name)
                append(" targetTag=").append(targetTag?.rawTag ?: "null")
                append(" hasActiveHint=").append(hasActiveHint)
                append(" alreadyShown=").append(alreadyShown)
                append(" lastAnyHintShownAt=").append(lastAnyHintShownAt)
                append(" nowMillis=").append(nowMillis)
                append(" cooldownMillis=").append(cooldownMillis)
                append(" cooldownActive=").append(cooldownActive)
            }
        )

        // Single-active invariant: пока один hint активен, второй не допускаем.
        if (hasActiveHint) {
            Log.d(
                TAG,
                "TRY_SHOW_REJECT reason=ACTIVE_EXISTS hintId=${hintId.name} activeHint=${_activeHint.value?.hintId?.name}"
            )
            return@withLock false
        }

        // Если hint уже был показан и подтверждён persisted state, повтор не допускаем.
        if (alreadyShown) {
            Log.d(
                TAG,
                "TRY_SHOW_REJECT reason=ALREADY_SHOWN hintId=${hintId.name}"
            )
            return@withLock false
        }

        // Глобальный cooldown между любыми hint.
        if (cooldownActive) {
            val remainingMs = (cooldownMillis - (nowMillis - lastAnyHintShownAt)).coerceAtLeast(0L)
            Log.d(
                TAG,
                "TRY_SHOW_REJECT reason=COOLDOWN hintId=${hintId.name} remainingMs=$remainingMs"
            )
            return@withLock false
        }

        // Активируем hint.
        _activeHint.value = ActiveHint(
            hintId = hintId,
            screen = screen,
            targetTag = targetTag,
            title = title,
            body = body,
            activatedAtMillis = nowMillis
        )

        // Фиксируем момент показа для глобального cooldown.
        repository.setLastAnyHintShownAt(nowMillis)

        Log.d(
            TAG,
            "TRY_SHOW_ACCEPT hintId=${hintId.name} screen=${screen.name} targetTag=${targetTag?.rawTag ?: "null"}"
        )

        return@withLock true
    }

    /**
     * Закрывает текущий активный hint.
     *
     * По MVP-правилу persisted shown-flag пишется только
     * для пользовательских сценариев dismiss/skip.
     */
    suspend fun dismiss(reason: OnboardingDismissReason) {
        mutex.withLock {
            val current = _activeHint.value

            if (current == null) {
                Log.d(
                    TAG,
                    "DISMISS_SKIP reason=NO_ACTIVE_HINT dismissReason=${reason.name}"
                )
                return@withLock
            }

            Log.d(
                TAG,
                "DISMISS_BEGIN hintId=${current.hintId.name} screen=${current.screen.name} dismissReason=${reason.name} persistShown=${reason.persistShownFlag}"
            )

            if (reason.persistShownFlag) {
                repository.markShown(current.hintId)
                Log.d(
                    TAG,
                    "DISMISS_MARK_SHOWN hintId=${current.hintId.name}"
                )
            }

            _activeHint.value = null

            Log.d(
                TAG,
                "DISMISS_END hintId=${current.hintId.name}"
            )
        }
    }
}