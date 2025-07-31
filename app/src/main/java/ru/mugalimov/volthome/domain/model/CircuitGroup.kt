package ru.mugalimov.volthome.domain.model

// Каждая подгруппа содержит полную информацию для карточки
data class CircuitGroup(
    val groupId: Long = 0, // Добавляем ID
    val groupNumber: Int,        // Уникальный номер группы
    val roomName: String,        // Название комнаты
    val roomId: Long,            // ID комнаты
    val groupType: DeviceType,   // Тип группы (освещение, розетки и т.д.)
    val devices: List<Device>,   // Устройства в группе

    // Расчетные параметры
    val nominalCurrent: Double,  // Суммарный расчетный ток группы (А)
    val circuitBreaker: Int,     // Номинал автомата (А)
    val cableSection: Double,    // Сечение кабеля (мм²)
    val breakerType: String,     // Тип автомата ("B", "C", "D")

    // Параметры безопасности
    val rcdRequired: Boolean,    // Требуется ли УЗО
    val rcdCurrent: Int = 30,     // Ток утечки для УЗО (мА)

    // Фазы
    val phase: Phase = Phase.A
)
