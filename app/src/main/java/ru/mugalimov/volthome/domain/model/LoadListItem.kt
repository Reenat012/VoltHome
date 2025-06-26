package ru.mugalimov.volthome.domain.model

sealed class LoadListItem {
    // Элемент для отображения суммарной нагрузки
    data class Total(val totalLoad: TotalLoad) : LoadListItem()

    // Элемент для отображения нагрузки комнаты
    data class Room(val roomWithLoad: RoomWithLoad) : LoadListItem()
}