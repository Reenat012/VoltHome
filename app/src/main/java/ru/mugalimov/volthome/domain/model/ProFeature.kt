package ru.mugalimov.volthome.domain.model

/**
 * Причина показа paywall ("Купить PRO?") при попытке использовать PRO-функцию.
 */
enum class ProFeature {
    // existing
    PROJECTS_LIMIT,
    PHASE_DND_TEASER,
    ADVANCED_DEVICE_EDITOR,

    // professional artifacts
    PRO_REPORT,          // полный профессиональный отчёт (в т.ч. PDF экспорт)
    CALC_EXPLANATIONS,   // обоснования / шаги / прозрачность расчётов
    CALC_WARNINGS        // предупреждения по расчёту/данным
}