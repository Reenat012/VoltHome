package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectObjectType
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectTemplate
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectTemplateDevice
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectTemplateRoom
import ru.mugalimov.volthome.ui.screens.project_wizard.ProjectWizardUiState
import ru.mugalimov.volthome.ui.screens.project_wizard.WizardRoomUi

class ProjectWizardUiStateTest {

    @Test
    fun `non-empty template cannot create a project without loads`() {
        val state = state(step = 3, deviceCount = 0)

        assertEquals(1, state.roomsCount)
        assertEquals(0, state.devicesCount)
        assertFalse(state.canContinue)
    }

    @Test
    fun `input power is validated only when user enables it`() {
        assertNull(state(step = 1, inputPowerKnown = false, inputPowerText = "").inputPowerError)
        assertNotNull(state(step = 1, inputPowerKnown = true, inputPowerText = "").inputPowerError)
        assertNotNull(state(step = 1, inputPowerKnown = true, inputPowerText = "0,1").inputPowerError)
        assertNull(state(step = 1, inputPowerKnown = true, inputPowerText = "15,0").inputPowerError)
    }

    @Test
    fun `power estimate uses device demand ratio and warns above available power`() {
        val state = state(
            step = 3,
            deviceCount = 2,
            inputPowerKnown = true,
            inputPowerText = "1,5"
        )

        assertEquals(4_000.0, state.estimatedInstalledPowerW, 0.01)
        assertEquals(2_000.0, state.estimatedCalculatedPowerW, 0.01)
        assertNotNull(state.inputPowerWarning)
        assertTrue(state.canContinue)
    }

    @Test
    fun `three-phase device blocks one-phase project`() {
        val device = defaultDevice(voltage = Voltage(400, VoltageType.AC_3PHASE))
        val state = state(step = 3, deviceCount = 1, device = device)

        assertEquals(listOf(device.name), state.incompatibleThreePhaseDevices)
        assertFalse(state.canContinue)
    }

    private fun state(
        step: Int,
        deviceCount: Int = 1,
        inputPowerKnown: Boolean = false,
        inputPowerText: String = "",
        device: DefaultDevice = defaultDevice()
    ): ProjectWizardUiState {
        val room = ProjectTemplateRoom(
            key = "room",
            title = "Комната",
            description = "Тест",
            roomType = RoomType.STANDARD,
            devices = listOf(ProjectTemplateDevice(device.id, 1))
        )
        val template = ProjectTemplate(
            id = "test",
            version = 1,
            objectType = ProjectObjectType.APARTMENT,
            title = "Тест",
            subtitle = "Тест",
            recommendedPhaseMode = PhaseMode.SINGLE,
            rooms = listOf(room)
        )
        return ProjectWizardUiState(
            isLoading = false,
            step = step,
            selectedTemplate = template,
            projectName = "Проект №1",
            phaseMode = PhaseMode.SINGLE,
            inputPowerKnown = inputPowerKnown,
            inputPowerText = inputPowerText,
            catalog = mapOf(device.id to device),
            rooms = listOf(WizardRoomUi(room, count = 1, deviceCounts = mapOf(device.id to deviceCount)))
        )
    }

    private fun defaultDevice(
        voltage: Voltage = Voltage(230, VoltageType.AC_1PHASE)
    ) = DefaultDevice(
        id = 1,
        name = "Тестовая нагрузка",
        power = 2_000,
        voltage = voltage,
        demandRatio = 0.5,
        deviceType = DeviceType.SOCKET,
        powerFactor = 1.0,
        hasMotor = false,
        requiresDedicatedCircuit = false,
        requiresSocketConnection = true
    )
}
