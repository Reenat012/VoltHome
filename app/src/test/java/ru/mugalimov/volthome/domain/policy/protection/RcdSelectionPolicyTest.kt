package ru.mugalimov.volthome.domain.policy.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.RoomType

class RcdSelectionPolicyTest {

    private val policy = RcdSelectionPolicy()

    @Test
    fun `general purpose socket in standard room requires 30 mA rcd`() {
        val result = policy.select(
            roomType = RoomType.STANDARD,
            devices = listOf(
                device(DeviceType.SOCKET, requiresSocketConnection = false)
            )
        )

        assertTrue(result.required)
        assertEquals(30, result.leakageCurrentMa)
        assertEquals(
            listOf(RcdSelectionReason.GENERAL_PURPOSE_SOCKET),
            result.reasons
        )
    }

    @Test
    fun `socket connected appliance on its own group requires 30 mA rcd`() {
        val result = policy.select(
            roomType = RoomType.STANDARD,
            devices = listOf(
                device(DeviceType.OTHER, requiresSocketConnection = true)
            )
        )

        assertTrue(result.required)
        assertEquals(30, result.leakageCurrentMa)
        assertEquals(
            listOf(RcdSelectionReason.SOCKET_CONNECTED_LOAD),
            result.reasons
        )
    }

    @Test
    fun `fixed lighting in standard room does not require rcd`() {
        val result = policy.select(
            roomType = RoomType.STANDARD,
            devices = listOf(
                device(DeviceType.LIGHTING, requiresSocketConnection = false)
            )
        )

        assertFalse(result.required)
    }

    @Test
    fun `special room requires 30 mA rcd for fixed load`() {
        val specialRooms = listOf(
            RoomType.BATHROOM,
            RoomType.KITCHEN,
            RoomType.OUTDOOR
        )

        specialRooms.forEach { roomType ->
            val result = policy.select(
                roomType = roomType,
                devices = listOf(
                    device(DeviceType.LIGHTING, requiresSocketConnection = false)
                )
            )

            assertTrue("$roomType must require RCD", result.required)
            assertEquals(30, result.leakageCurrentMa)
            assertTrue(result.reasons.contains(RcdSelectionReason.SPECIAL_ROOM))
        }
    }

    @Test
    fun `decision keeps every matching reason`() {
        val result = policy.select(
            roomType = RoomType.BATHROOM,
            devices = listOf(
                device(DeviceType.SOCKET, requiresSocketConnection = false)
            )
        )

        assertEquals(
            listOf(
                RcdSelectionReason.SPECIAL_ROOM,
                RcdSelectionReason.GENERAL_PURPOSE_SOCKET
            ),
            result.reasons
        )
    }

    @Test
    fun `special room socket connected load keeps every reason`() {
        val result = policy.select(
            roomType = RoomType.KITCHEN,
            devices = listOf(
                device(DeviceType.OTHER, requiresSocketConnection = true)
            )
        )

        assertEquals(
            listOf(
                RcdSelectionReason.SPECIAL_ROOM,
                RcdSelectionReason.SOCKET_CONNECTED_LOAD
            ),
            result.reasons
        )
    }

    private fun device(
        type: DeviceType,
        requiresSocketConnection: Boolean
    ) = RcdDeviceInput(
        deviceType = type,
        requiresSocketConnection = requiresSocketConnection
    )
}
