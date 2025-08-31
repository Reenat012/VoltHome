// ui/viewmodel/RoomDetailAction.kt
package ru.mugalimov.volthome.ui.viewmodel

sealed interface RoomDetailAction {
    data class DevicesAdded(val roomId: Long, val deviceIds: List<Long>) : RoomDetailAction
    data class UserMessage(val message: String) : RoomDetailAction
    data class Error(val throwable: Throwable) : RoomDetailAction
}