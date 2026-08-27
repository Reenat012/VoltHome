package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.ui.navigation.BottomNavItem
import ru.mugalimov.volthome.ui.navigation.Screens
import ru.mugalimov.volthome.ui.navigation.mainBottomNavItems

class BottomNavigationStructureTest {

    @Test
    fun `panel is a dedicated fourth bottom destination`() {
        assertEquals(4, mainBottomNavItems.size)
        assertEquals(Screens.PanelVisualizationScreen.route, mainBottomNavItems.last().route)
        assertEquals("Щит", mainBottomNavItems.last().title)
        assertTrue(mainBottomNavItems.last() is BottomNavItem.Panel)
    }

    @Test
    fun `explication is labelled as lines`() {
        assertEquals("Линии", BottomNavItem.Explication.title)
    }
}
