package ru.mugalimov.volthome.domain.model

// Профиль безопасности для помещения
data class SafetyProfile(
    val rcdRequired: Boolean = false,  // Требуется ли УЗО
    val rcdCurrent: Int = 30           // Ток утечки (мА)
)