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

    /**
     * Главная подсказка ручного режима на Экспликации:
     * пользователь должен понять, что перенос начинается с long-press по устройству.
     */
    val EXPLICATION_LONG_PRESS_DEVICE = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_LONG_PRESS_DEVICE,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_FIRST_DEVICE_CHIP,
        priority = 140,
        title = "Зажми устройство, чтобы перенести",
        body = "Удерживай устройство long-press. После этого сверху откроется панель переноса, и можно выбрать новую группу или вынести устройство в нераспределённые."
    )

    /**
     * Вторая ключевая подсказка:
     * ручной режим не “сохраняется сам”, пользователь должен завершать его через чип.
     */
    val EXPLICATION_SAVE_MANUAL_CHANGES = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_SAVE_MANUAL_CHANGES,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_MANUAL_MODE_CHIP,
        priority = 130,
        title = "Сохранение — через кнопку «Ручной»",
        body = "Когда закончишь правки, нажми кнопку «Ручной» вверху. Там можно сохранить изменения, отменить их или остаться в ручном режиме."
    )

    /**
     * Более общий explanatory/legal hint.
     * Он нужен, но уже после action-подсказок.
     */
    val EXPLICATION_MANUAL_MODE_INFO = AdvancedHintSpec(
        hintId = OnboardingHintId.EXPLICATION_MANUAL_MODE_INFO,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_MANUAL_LEGAL_BANNER,
        priority = 120,
        title = "Ручной режим меняет структуру щита вручную",
        body = "Здесь ты задаёшь логику вручную: переносишь устройства, создаёшь новые группы и меняешь итоговую структуру. После таких правок результат нужно инженерно проверить."
    )

    /**
     * Selector для Экспликации.
     *
     * Важно:
     * - порядок не через if/else-цепочку в UI;
     * - все кандидаты дальше режутся по приоритету.
     */
    fun forExplicationHints(facts: ExplicationOnboardingFacts): List<AdvancedHintSpec> {
        if (!facts.isSuccess) return emptyList()
        if (!facts.manualModeActive) return emptyList()
        if (facts.hasBlockingState) return emptyList()

        return buildList {
            if (facts.groupsCount > 0) {
                add(EXPLICATION_LONG_PRESS_DEVICE)
                add(EXPLICATION_SAVE_MANUAL_CHANGES)
            }
            add(EXPLICATION_MANUAL_MODE_INFO)
        }
    }
}