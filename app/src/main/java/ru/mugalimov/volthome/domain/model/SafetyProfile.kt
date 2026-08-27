package ru.mugalimov.volthome.domain.model

import ru.mugalimov.volthome.domain.policy.protection.RcdSelectionReason

// Профиль безопасности для помещения
data class SafetyProfile(
    val rcdRequired: Boolean = false,  // Требуется ли УЗО
    val rcdCurrent: Int = 30,          // Ток утечки (мА)
    val reasons: List<RcdSelectionReason> = emptyList()
)
