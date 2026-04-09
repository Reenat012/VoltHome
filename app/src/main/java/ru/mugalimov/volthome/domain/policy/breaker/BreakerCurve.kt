package ru.mugalimov.volthome.domain.policy.breaker

/**
 * Поддерживаемые кривые автомата.
 *
 * Пока ограничиваемся теми значениями,
 * которые уже реально использует продукт.
 */
enum class BreakerCurve {
    B,
    C,
    D
}