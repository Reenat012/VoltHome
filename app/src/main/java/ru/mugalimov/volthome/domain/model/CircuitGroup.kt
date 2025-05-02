package ru.mugalimov.volthome.domain.model

// Каждая подгруппа содержит полную информацию для карточки
data class CircuitGroup(
    val groupId: Long = 0,
    val roomId: Long,
    val roomName: String,     // Название комнаты
    val groupType: DeviceType,// Тип группы (LIGHTING/SOCKET)
    val devices: List<Device>,// Устройства в группе
    val nominalCurrent: Double,// Суммарный ток группы
    val circuitBreaker: Int,  // Номинал автомата (10А/16А)
    val cableSection: Double, // Сечение кабеля (1.5/2.5 мм²)
    val groupNumber: Int      // Порядковый номер группы в рамках типа
)
