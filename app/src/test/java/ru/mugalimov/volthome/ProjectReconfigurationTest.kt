package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.reconfiguration.ReconfigurationImpact
import ru.mugalimov.volthome.domain.use_case.reconfiguration.calculateMaxPhaseCurrent

class ProjectReconfigurationTest {
    @Test
    fun `phase change requires structural rebuild`() {
        val impact = impact(
            currentMode = PhaseMode.THREE,
            proposedMode = PhaseMode.SINGLE,
            phaseChanged = true,
            powerChanged = false
        )

        assertTrue(impact.hasChanges)
        assertTrue(impact.requiresStructuralRebuild)
    }

    @Test
    fun `input power only does not rebuild groups`() {
        val impact = impact(
            currentMode = PhaseMode.THREE,
            proposedMode = PhaseMode.THREE,
            phaseChanged = false,
            powerChanged = true
        )

        assertTrue(impact.hasChanges)
        assertFalse(impact.requiresStructuralRebuild)
    }

    @Test
    fun `three phase loads contribute to every phase maximum`() {
        val groups = listOf(
            group(Phase.A, 8.0),
            group(Phase.B, 6.0),
            group(Phase.C, 7.0),
            group(Phase.THREE_PHASE, 4.0)
        )

        assertEquals(12.0, calculateMaxPhaseCurrent(groups), 0.001)
    }

    private fun impact(
        currentMode: PhaseMode,
        proposedMode: PhaseMode,
        phaseChanged: Boolean,
        powerChanged: Boolean
    ) = ReconfigurationImpact(
        currentPhaseMode = currentMode,
        proposedPhaseMode = proposedMode,
        currentInputPowerKw = 15.0,
        proposedInputPowerKw = if (powerChanged) 20.0 else 15.0,
        currentGroupsCount = 4,
        proposedGroupsCount = 4,
        devicesCount = 5,
        calculatedPowerKw = 8.0,
        currentMaxPhaseCurrentA = 10.0,
        proposedMaxPhaseCurrentA = 10.0,
        hasManualStructure = false,
        hasActiveManualSession = false,
        phaseModeChanged = phaseChanged,
        inputPowerChanged = powerChanged
    )

    private fun group(phase: Phase, current: Double) = CircuitGroup(
        groupNumber = 1,
        roomName = "Комната",
        roomId = 1,
        groupType = DeviceType.SOCKET,
        devices = emptyList(),
        nominalCurrent = current,
        installedPowerW = 1000,
        circuitBreaker = 16,
        cableSection = 2.5,
        breakerType = "C",
        rcdRequired = true,
        phase = phase
    )
}
