package ru.mugalimov.volthome.ui.onboarding

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
 * Важно:
 * - coordinator ничего не знает о predicate/блокировках;
 * - coordinator только применяет single-active + persisted shown + cooldown.
 */
@Singleton
class OnboardingCoordinator @Inject constructor(
    private val repository: OnboardingRepository
) {

    companion object {
        /**
         * Для base hints и advanced hints 30 секунд слишком много:
         * пользователь проходит несколько экранов быстрее, чем истекает cooldown.
         *
         * Поэтому держим короткий глобальный cooldown.
         */
        const val DEFAULT_COOLDOWN_MILLIS: Long = 3_000L

        // Единый тег логов coordinator-а.
        private const val TAG = "ONBOARD_COORD"
    }

    private val mutex = Mutex()

    // Храним максимум одну активную подсказку.
    private val _activeHint = MutableStateFlow<ActiveHint?>(null)
    val activeHint: StateFlow<ActiveHint?> = _activeHint.asStateFlow()
    private var presentedHintActivatedAt: Long? = null
    private var currentScreen: OnboardingScreen? = null
    private var presentedOnCurrentVisit: Boolean = false

    /**
     * Пытается активировать hint.
     *
     * targetTag может быть null:
     * - тогда host использует centered fallback.
     */
    suspend fun tryShow(
        hintId: OnboardingHintId,
        screen: OnboardingScreen,
        targetTag: OnboardingTargetTag? = null,
        title: String,
        body: String,
        nowMillis: Long = System.currentTimeMillis(),
        cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS
    ): Boolean {
        var effectiveNowMillis = nowMillis

        while (true) {
            var remainingCooldownMillis = 0L
            var waitForActiveHint = false
            val accepted = mutex.withLock {
                val hasActiveHint = (_activeHint.value != null)

                // Сначала читаем persisted state и cooldown для прозрачной диагностики.
                val alreadyShown = repository.isShown(hintId)
                val hintsEnabled = repository.areHintsEnabled()
                val lastAnyHintShownAt = repository.getLastAnyHintShownAt()
                val cooldownActive =
                    lastAnyHintShownAt > 0L &&
                            (effectiveNowMillis - lastAnyHintShownAt) < cooldownMillis

                Log.d(
                    TAG,
                    buildString {
                        append("TRY_SHOW")
                        append(" hintId=").append(hintId.name)
                        append(" screen=").append(screen.name)
                        append(" targetTag=").append(targetTag?.rawTag ?: "null")
                        append(" hasActiveHint=").append(hasActiveHint)
                        append(" alreadyShown=").append(alreadyShown)
                        append(" hintsEnabled=").append(hintsEnabled)
                        append(" lastAnyHintShownAt=").append(lastAnyHintShownAt)
                        append(" nowMillis=").append(effectiveNowMillis)
                        append(" cooldownMillis=").append(cooldownMillis)
                        append(" cooldownActive=").append(cooldownActive)
                    }
                )

                // Запрос не теряем: ждём освобождения active slot. Корутинa
                // привязана к экранному LaunchedEffect и отменится при уходе.
                if (hasActiveHint) {
                    Log.d(
                        TAG,
                        "TRY_SHOW_WAIT reason=ACTIVE_EXISTS hintId=${hintId.name} activeHint=${_activeHint.value?.hintId?.name}"
                    )
                    waitForActiveHint = true
                    return@withLock false
                }

                // Если hint уже был подтверждён, повтор не допускаем.
                if (alreadyShown) {
                    Log.d(
                        TAG,
                        "TRY_SHOW_REJECT reason=ALREADY_SHOWN hintId=${hintId.name}"
                    )
                    return false
                }

                if (!hintsEnabled) {
                    Log.d(TAG, "TRY_SHOW_REJECT reason=HINTS_DISABLED hintId=${hintId.name}")
                    return false
                }

                // Не устраиваем каскад карточек: максимум одна фактически
                // показанная подсказка за одно посещение экрана.
                if (currentScreen == screen && presentedOnCurrentVisit) {
                    Log.d(
                        TAG,
                        "TRY_SHOW_REJECT reason=ALREADY_PRESENTED_THIS_VISIT hintId=${hintId.name}"
                    )
                    return false
                }

                // Cooldown теперь не теряет запрос: ждём остаток времени, после чего
                // повторно проверяем persisted/active state.
                if (cooldownActive) {
                    remainingCooldownMillis =
                        (cooldownMillis - (effectiveNowMillis - lastAnyHintShownAt))
                            .coerceAtLeast(1L)
                    Log.d(
                        TAG,
                        "TRY_SHOW_WAIT reason=COOLDOWN hintId=${hintId.name} remainingMs=$remainingCooldownMillis"
                    )
                    return@withLock false
                }

                // Активируем hint. Момент фактического показа host подтвердит
                // отдельно, когда anchor стабилизируется.
                _activeHint.value = ActiveHint(
                    hintId = hintId,
                    screen = screen,
                    targetTag = targetTag,
                    title = title,
                    body = body,
                    activatedAtMillis = effectiveNowMillis
                )
                presentedHintActivatedAt = null

                Log.d(
                    TAG,
                    "TRY_SHOW_ACCEPT hintId=${hintId.name} screen=${screen.name} targetTag=${targetTag?.rawTag ?: "null"}"
                )
                true
            }

            if (accepted) return true

            if (waitForActiveHint) {
                activeHint.filter { it == null }.first()
            } else {
                delay(remainingCooldownMillis)
            }
            effectiveNowMillis = System.currentTimeMillis()
        }
    }

    /**
     * Вызывается host-ом только после того, как целевой элемент найден и
     * карточка действительно готова появиться на экране.
     */
    suspend fun markPresented(hintId: OnboardingHintId) {
        mutex.withLock {
            val current = _activeHint.value ?: return@withLock
            if (current.hintId != hintId) return@withLock
            if (presentedHintActivatedAt == current.activatedAtMillis) return@withLock

            repository.setLastAnyHintShownAt(System.currentTimeMillis())
            presentedHintActivatedAt = current.activatedAtMillis
            presentedOnCurrentVisit = true
            Log.d(TAG, "PRESENTED hintId=${hintId.name}")
        }
    }

    /**
     * Не даёт подсказке предыдущего экрана скрыто блокировать всю очередь.
     */
    suspend fun onScreenChanged(newScreen: OnboardingScreen?) {
        val shouldCancel = mutex.withLock {
            if (currentScreen != newScreen) {
                currentScreen = newScreen
                presentedOnCurrentVisit = false
            }
            val current = _activeHint.value
            current != null && current.screen != newScreen
        }
        if (shouldCancel) dismiss(OnboardingDismissReason.SYSTEM_CANCELLED)
    }

    suspend fun resetAll() {
        mutex.withLock {
            _activeHint.value = null
            presentedHintActivatedAt = null
            presentedOnCurrentVisit = false
            repository.resetAll()
        }
    }

    suspend fun disableAll() {
        mutex.withLock {
            _activeHint.value = null
            presentedHintActivatedAt = null
            repository.disableHints()
        }
    }

    /**
     * Закрывает текущий активный hint.
     *
     * Persisted shown-flag пишется только после явного «Понятно».
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
            presentedHintActivatedAt = null

            Log.d(
                TAG,
                "DISMISS_END hintId=${current.hintId.name}"
            )
        }
    }
}
