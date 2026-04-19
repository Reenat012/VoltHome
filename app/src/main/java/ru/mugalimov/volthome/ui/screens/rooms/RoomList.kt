package ru.mugalimov.volthome.ui.screens.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor

/**
 * Список комнат (реактивная витрина RoomsList).
 * Единственный источник истины для карточки — RoomWithDevicesPreviewUi.
 */
@Composable
fun RoomList(
    rooms: List<RoomWithDevicesPreviewUi>,
    onClickRoom: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        itemsIndexed(
            items = rooms,
            key = { _, item -> item.roomId },
            contentType = { _, _ -> "room_card" } // ✅ стабилизируем реюз/измерения айтемов
        ) { index, room ->
            val isFirstRoomTarget = index == 0

            Box(
                modifier = if (isFirstRoomTarget) {
                    Modifier
                        .testTag(OnboardingTargetTag.ROOMS_FIRST_CARD.rawTag)
                        .onboardingAnchor(
                            targetTag = OnboardingTargetTag.ROOMS_FIRST_CARD,
                            screenId = OnboardingScreen.ROOMS
                        )
                } else {
                    Modifier
                }
            ) {
                RoomCard(
                    room = room,
                    onClick = { onClickRoom(room.roomId) },
                    onDelete = { onDelete(room.roomId) }
                )
            }
        }
    }
}