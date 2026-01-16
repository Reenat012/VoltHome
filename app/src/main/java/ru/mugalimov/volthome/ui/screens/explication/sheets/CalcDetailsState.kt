package ru.mugalimov.volthome.ui.screens.explication.sheets

enum class CalcDetailsState {
    AVAILABLE,
    LOCKED,
    NONE,

    /**
     * Расчёт не применим (справочный sheet).
     * Секция "Расчёт" не должна отображаться.
     */
    HIDDEN
}