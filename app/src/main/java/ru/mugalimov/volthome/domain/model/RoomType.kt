package ru.mugalimov.volthome.domain.model

// Типы помещений для определения специальных требований безопасности
enum class RoomType {
    STANDARD,   // Обычные помещения
    BATHROOM,   // Ванная комната (требуется УЗО)
    KITCHEN,    // Кухня (требуется УЗО)
    OUTDOOR     // Уличные розетки (требуется УЗО)
}