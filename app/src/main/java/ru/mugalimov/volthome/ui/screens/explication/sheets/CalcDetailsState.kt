package ru.mugalimov.volthome.ui.screens.explication.sheets

enum class CalcDetailsState {
    NONE,       // шаги расчёта отсутствуют в данных
    LOCKED,     // шаги есть, но закрыты тарифом
    AVAILABLE   // шаги есть и доступны
}