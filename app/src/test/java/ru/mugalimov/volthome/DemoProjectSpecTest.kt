package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.DemoProjectSpec
import ru.mugalimov.volthome.domain.model.RoomType

class DemoProjectSpecTest {

    @Test
    fun `demo marker is explicit and does not match user notes`() {
        assertTrue(DemoProjectSpec.isDemo(DemoProjectSpec.NOTE_MARKER))
        assertFalse(DemoProjectSpec.isDemo(null))
        assertFalse(DemoProjectSpec.isDemo("Пользовательская заметка"))
    }

    @Test
    fun `demo contains representative rooms and devices`() {
        val rooms = DemoProjectSpec.rooms

        assertEquals(3, rooms.size)
        assertTrue(rooms.any { it.roomType == RoomType.STANDARD })
        assertTrue(rooms.any { it.roomType == RoomType.KITCHEN })
        assertTrue(rooms.any { it.roomType == RoomType.BATHROOM })
        assertTrue(rooms.sumOf { room -> room.devices.sumOf { it.count } } >= 10)
    }
}
