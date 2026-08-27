package ru.mugalimov.volthome.domain.use_case.manual

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.manual.ManualDeviceDraft
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState

class ValidateManualDraftUseCaseTest {
    private val validator = ValidateManualDraftUseCase()

    @Test
    fun `valid calculated draft passes`() {
        val result = validator.execute(draft(), requireCalculatedLines = true)
        assertTrue(result.violations.joinToString(), result.isValid)
    }

    @Test
    fun `duplicate device membership is rejected`() {
        val base = draft()
        val duplicate = base.groups.first().copy(groupId = 2, groupNumber = 2)
        val result = validator.execute(
            base.copy(groups = base.groups + duplicate),
            requireCalculatedLines = true
        )
        assertFalse(result.isValid)
        assertTrue(result.violations.any { it.code == "DUPLICATE_MEMBERSHIP" })
    }

    @Test
    fun `manual draft keeps incompatible phase as warning`() {
        val base = draft()
        val result = validator.execute(
            base.copy(
                phaseMode = PhaseMode.SINGLE,
                groups = base.groups.map { it.copy(phase = Phase.THREE_PHASE) }
            ),
            requireCalculatedLines = true
        )
        assertTrue(result.violations.joinToString(), result.isValid)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `assigned and unassigned intersection is rejected`() {
        val base = draft()
        val result = validator.execute(
            base.copy(unassignedDeviceIds = setOf(1)),
            requireCalculatedLines = true
        )
        assertFalse(result.isValid)
        assertTrue(result.violations.any { it.code == "ASSIGNED_AND_UNASSIGNED" })
    }

    private fun draft(): ProjectEditState {
        val device = ManualDeviceDraft(
            deviceId = 1,
            roomId = 1,
            roomName = "Комната",
            roomType = RoomType.STANDARD,
            deviceType = DeviceType.SOCKET,
            powerW = 1000,
            voltageType = VoltageType.AC_1PHASE,
            voltageValue = 230,
            demandRatio = 0.8,
            powerFactor = 0.9,
            hasMotor = false,
            requiresDedicatedCircuit = false,
            requiresSocketConnection = true
        )
        val group = ManualGroupDraft(
            groupId = 1,
            groupNumber = 1,
            roomId = 1,
            roomName = "Комната",
            roomType = RoomType.STANDARD,
            groupType = DeviceType.SOCKET,
            phase = Phase.A,
            deviceIds = listOf(1),
            nominalCurrent = 3.9,
            circuitBreaker = 16,
            cableSection = 2.5,
            breakerType = "C",
            rcdRequired = true,
            rcdCurrent = 30
        )
        return ProjectEditState(
            projectId = "p1",
            groups = listOf(group),
            devices = listOf(device),
            unassignedDeviceIds = emptySet(),
            nextGroupNumber = 2,
            phaseMode = PhaseMode.THREE,
            roomTypesById = mapOf(1L to RoomType.STANDARD)
        )
    }
}
