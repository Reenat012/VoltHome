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
        title = "Сохраните профессиональный PDF-отчёт",
        body = "В отчёт войдут сводка, группы, защита и расчётные допущения. Его можно передать клиенту; перед монтажом результаты должен проверить специалист."
    )

    fun forExplication(facts: ExplicationOnboardingFacts): AdvancedHintSpec? {
        if (!facts.isSuccess) return null
        if (!facts.pdfAvailable) return null
        if (facts.pdfShown) return null
        if (facts.hasBlockingState) return null
        return PDF_EXPORT_INFO
    }
}
