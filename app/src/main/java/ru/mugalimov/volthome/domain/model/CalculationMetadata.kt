package ru.mugalimov.volthome.domain.model

/**
 * Метаданные, позволяющие отличить решение алгоритма от ручного изменения.
 */
enum class CalculationSource {
    AUTO,
    MANUAL,
    LEGACY
}

object CalculationAlgorithm {
    const val VERSION = 5
}
