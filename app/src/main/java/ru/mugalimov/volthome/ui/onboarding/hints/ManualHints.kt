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
 * - сначала даём action-oriented hints;
 * - общую legal/info подсказку держим ниже по приоритету.
 */
object ManualHints {

    val EXPLICATION_MANUAL_MODE_INFO = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_MANUAL_MODE_INFO,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_MANUAL_LEGAL_BANNER,
        priority = 120,
        title = "Ручной режим меняет структуру щита вручную",
        body = "Здесь ты задаёшь логику вручную: переносишь устройства, создаёшь новые группы и меняешь итоговую структуру. После таких правок результат нужно инженерно проверить."
    )

    val EXPLICATION_LONG_PRESS_DEVICE = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_LONG_PRESS_DEVICE,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_FIRST_DEVICE_CHIP,
        priority = 140,
        title = "Зажми устройство, чтобы перенести",
        body = "Удерживай устройство long-press. После этого сверху откроется панель переноса, и можно выбрать новую группу или вынести устройство в нераспределённые."
    )

    val EXPLICATION_SAVE_MANUAL_CHANGES = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_SAVE_MANUAL_CHANGES,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_MANUAL_MODE_CHIP,
        priority = 130,
        title = "Сохранение — через кнопку «Ручной»",
        body = "Когда закончишь правки, нажми кнопку «Ручной» вверху. Там можно сохранить изменения, отменить их или остаться в ручном режиме."
    )

    fun forExplicationStep(facts: ExplicationOnboardingFacts): AdvancedHintSpec? {
        if (!facts.isSuccess) return null
        if (!facts.manualModeActive) return null
        if (facts.hasBlockingState) return null

        return when {
            !facts.longPressShown && facts.groupsCount > 0 -> EXPLICATION_LONG_PRESS_DEVICE
            !facts.saveShown && facts.groupsCount > 0 -> EXPLICATION_SAVE_MANUAL_CHANGES
            !facts.manualIntroShown -> EXPLICATION_MANUAL_MODE_INFO
            else -> null
        }
    }
}