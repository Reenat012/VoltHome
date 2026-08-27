package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.mugalimov.volthome.ui.model.ManualModeControlAvailability
import ru.mugalimov.volthome.ui.model.ManualModeUiPolicy
import ru.mugalimov.volthome.ui.navigation.Screens

class ManualModeUiPolicyTest {

    @Test
    fun `loads and shield allow entering manual editor`() {
        listOf(
            Screens.LoadsScreen.route,
            Screens.ExplicationScreen.route
        ).forEach { route ->
            assertEquals(
                ManualModeControlAvailability.EDITABLE,
                ManualModeUiPolicy.availability(route, manualModeActive = false)
            )
        }
    }

    @Test
    fun `unsupported screen hides inactive manual control`() {
        assertEquals(
            ManualModeControlAvailability.HIDDEN,
            ManualModeUiPolicy.availability(
                Screens.RoomsList.route,
                manualModeActive = false
            )
        )
    }

    @Test
    fun `unsupported screen keeps active or saved manual state visible`() {
        assertEquals(
            ManualModeControlAvailability.STATUS_ONLY,
            ManualModeUiPolicy.availability(
                Screens.PanelVisualizationScreen.route,
                manualModeActive = true
            )
        )
    }
}
