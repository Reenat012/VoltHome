package ru.mugalimov.volthome.ui.onboarding.hints

import ru.mugalimov.volthome.ui.onboarding.model.ExplicationOnboardingFacts
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag

/**
 * Подсказки для PDF.
 */
object PdfHints {

    val PDF_EXPORT_INFO = AdvancedHintSpec(
        hintId = OnboardingHintId.PDF_EXPORT_INFO,
        screen = OnboardingScreen.EXPLICATION,
        targetTag = OnboardingTargetTag.EXPLICATION_PDF_FAB,
        priority = 100,
        title = "PDF — это отчёт, а не акт допуска",
        body = "Экспорт сохраняет расчётную сводку проекта. Документ не заменяет проверку инженером и не подтверждает соответствие монтажа на объекте."
    )

    fun forExplication(facts: ExplicationOnboardingFacts): AdvancedHintSpec? {
        if (!facts.isSuccess) return null
        if (!facts.pdfAvailable) return null
        if (facts.hasBlockingState) return null
        return PDF_EXPORT_INFO
    }
}