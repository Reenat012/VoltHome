package ru.mugalimov.volthome.ui.screens.rooms

import ru.mugalimov.volthome.domain.model.RoomType

data class RoomWithDevicesPreviewUi(
    val roomId: Long,
    val name: String,
    val roomType: RoomType,
    val devicesCount: Int,
    val devicesPreview: List<String>
)

data class RoomUiState(
    val isLoading: Boolean = false,
    val error: Throwable? = null,
    val roomsPreview: List<RoomWithDevicesPreviewUi> = emptyList()
)