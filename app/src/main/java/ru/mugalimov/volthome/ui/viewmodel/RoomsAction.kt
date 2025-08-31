package ru.mugalimov.volthome.ui.viewmodel

sealed interface RoomsAction {
    /** Комната создана: можно перейти на экран комнаты и показать Undo */
    data class RoomCreated(val roomId: Long, val deviceIds: List<Long>) : RoomsAction

    /** В существующую комнату добавлены устройства: показать Undo */
    data class DevicesAdded(val roomId: Long, val deviceIds: List<Long>) : RoomsAction

    /** Юзерские ошибки (дубликат имени, пустое имя и т.п.) */
    data class UserMessage(val message: String) : RoomsAction

    /** Неожиданная ошибка (для логов/крэшлитикса) */
    data class Error(val throwable: Throwable) : RoomsAction
}