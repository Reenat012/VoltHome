package ru.mugalimov.volthome.ui.screens.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.mugalimov.volthome.domain.model.panel.PanelEnclosureConfig

class PanelEnclosureInputTest {

    @Test
    fun `custom module count is accepted`() {
        assertEquals(
            PanelEnclosureConfig(modulesPerRail = 17, railCount = 3),
            parsePanelEnclosureInput(railText = "3", modulesText = "17")
        )
    }

    @Test
    fun `values outside enclosure limits are rejected`() {
        assertNull(parsePanelEnclosureInput(railText = "0", modulesText = "17"))
        assertNull(parsePanelEnclosureInput(railText = "3", modulesText = "73"))
        assertNull(parsePanelEnclosureInput(railText = "", modulesText = "18"))
    }
}
