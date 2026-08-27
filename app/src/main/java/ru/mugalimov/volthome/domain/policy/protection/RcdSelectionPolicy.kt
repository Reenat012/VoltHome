package ru.mugalimov.volthome.domain.policy.protection

import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.RoomType

/**
 * Явные продуктовые правила выбора группового УЗО.
 *
 * Защита выбирается по фактическому окончанию проектируемой линии:
 * - сама бытовая розетка;
 * - либо любой прибор, подключаемый к этой отдельной линии через розетку.
 *
 * Если приложение показывает такую нагрузку отдельной группой с собственным
 * автоматом и кабелем, эта группа является отдельной розеточной линией.
 */
class RcdSelectionPolicy {

    fun select(
        roomType: RoomType,
        devices: List<RcdDeviceInput>
    ): RcdSelectionDecision {
        val reasons = buildList {
            if (roomType in SPECIAL_ROOM_TYPES) {
                add(RcdSelectionReason.SPECIAL_ROOM)
            }

            if (devices.any(RcdDeviceInput::isGeneralPurposeSocket)) {
                add(RcdSelectionReason.GENERAL_PURPOSE_SOCKET)
            }

            if (devices.any { it.requiresSocketConnection }) {
                add(RcdSelectionReason.SOCKET_CONNECTED_LOAD)
            }
        }

        return RcdSelectionDecision(
            required = reasons.isNotEmpty(),
            leakageCurrentMa = if (reasons.isNotEmpty()) DEFAULT_LEAKAGE_CURRENT_MA else null,
            reasons = reasons
        )
    }

    private companion object {
        const val DEFAULT_LEAKAGE_CURRENT_MA = 30

        val SPECIAL_ROOM_TYPES = setOf(
            RoomType.BATHROOM,
            RoomType.KITCHEN,
            RoomType.OUTDOOR
        )
    }
}

data class RcdDeviceInput(
    val deviceType: DeviceType,
    val requiresSocketConnection: Boolean
) {
    /**
     * SOCKET + false означает саму бытовую розетку в проекте.
     * SOCKET + true означает нагрузку, которая подключается к розетке.
     */
    val isGeneralPurposeSocket: Boolean
        get() = deviceType == DeviceType.SOCKET && !requiresSocketConnection
}

data class RcdSelectionDecision(
    val required: Boolean,
    val leakageCurrentMa: Int?,
    val reasons: List<RcdSelectionReason>
)

enum class RcdSelectionReason {
    SPECIAL_ROOM,
    GENERAL_PURPOSE_SOCKET,
    SOCKET_CONNECTED_LOAD
}
