package ru.mugalimov.volthome.domain.model

/**
 * Причина показа paywall ("Купить PRO?") при попытке использовать PRO-функцию.
 */
enum class ProFeature {
    PROJECTS_LIMIT,
    PDF_EXPORT,
    PHASE_DND_TEASER,
    ADVANCED_DEVICE_EDITOR
}