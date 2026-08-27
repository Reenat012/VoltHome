package ru.mugalimov.volthome.ui.onboarding.hints

import ru.mugalimov.volthome.ui.onboarding.model.ExplicationOnboardingFacts
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag

/**
 * Подсказки для ручного режима.
 *
 * Важно:
 * - manual hints не показываем при blocking state;
 * - сначала объясняем режим и ответственность;
 * - затем показываем действие и сохранение.
 */
object ManualHints {

    val EXPLICATION_MANUAL_MODE_INFO = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_MANUAL_MODE_INFO,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_MANUAL_LEGAL_BANNER,
        priority = 120,
        title = "Ручной режим даёт полный контроль",
        body = "Здесь можно менять структуру щита самостоятельно. Приложение предупредит о спорных изменениях, но не ограничит ручную компоновку. Перед монтажом результат требует инженерной проверки."
    )

    val EXPLICATION_LONG_PRESS_DEVICE = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_LONG_PRESS_DEVICE,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_FIRST_DEVICE_CHIP,
        priority = 140,
        title = "Нажмите и удерживайте устройство",
        body = "Сверху откроется панель переноса. Выберите целевую группу или оставьте устройство нераспределённым."
    )

    val EXPLICATION_SAVE_MANUAL_CHANGES = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_SAVE_MANUAL_CHANGES,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_MANUAL_MODE_CHIP,
        priority = 130,
        title = "Сохранение — через кнопку «Ручной»",
        body = "Когда закончите правки, нажмите кнопку «Ручной» вверху. Там можно сохранить изменения, отменить их или остаться в ручном режиме."
    )

    fun forExplicationStep(facts: ExplicationOnboardingFacts): AdvancedHintSpec? {
        if (!facts.isSuccess) return null
        if (!facts.manualModeActive) return null
        if (facts.hasBlockingState) return null

        return when {
            !facts.manualIntroShown -> EXPLICATION_MANUAL_MODE_INFO
            !facts.longPressShown && facts.groupsCount > 0 -> EXPLICATION_LONG_PRESS_DEVICE
            !facts.saveShown && facts.groupsCount > 0 -> EXPLICATION_SAVE_MANUAL_CHANGES
            else -> null
        }
    }
}
