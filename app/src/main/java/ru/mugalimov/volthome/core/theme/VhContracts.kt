package ru.mugalimov.volthome.core.theme

/**
 * UI-контракты для семантических сущностей.
 * Не завязаны на domain-модели, чтобы не тащить зависимости слоями.
 */
enum class UiPhase {
    A, B, C, THREE
}

enum class UiStatus {
    INFO, SUCCESS, WARNING, ERROR
}


/**
 * UI-статус баланса фаз. Это именно "сигнал" для UI (какой текст показать),
 * а цвет UI сам выведет через UiStatus/VhColors.
 */
enum class UiBalanceStatus {
    OK, MINOR, HIGH
}