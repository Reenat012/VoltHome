package ru.mugalimov.volthome.domain.policy.compatibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.VoltageType

class CircuitCompatibilityPolicyTest {

    @Test
    fun `same room and connection is allowed`() {
        val decision = CircuitCompatibilityPolicy.evaluatePlacement(
            device = device(id = 2),
            target = group(devices = listOf(device(id = 1)))
        )
        assertTrue(decision.allowed)
        assertEquals(CircuitCompatibilityPolicy.Status.ALLOWED, decision.status)
    }

    @Test
    fun `cross room placement is rejected`() {
        val decision = CircuitCompatibilityPolicy.evaluatePlacement(
            device = device(id = 2, roomId = 2),
            target = group(devices = listOf(device(id = 1, roomId = 1)))
        )
        assertFalse(decision.allowed)
        assertTrue(CircuitCompatibilityPolicy.Reason.CROSS_ROOM in decision.reasons)
    }

    @Test
    fun `socket and fixed connection cannot be mixed`() {
        val decision = CircuitCompatibilityPolicy.evaluatePlacement(
            device = device(id = 2, socket = false),
            target = group(devices = listOf(device(id = 1, socket = true)))
        )
        assertFalse(decision.allowed)
        assertTrue(CircuitCompatibilityPolicy.Reason.MIXED_CONNECTION_TYPE in decision.reasons)
    }

    @Test
    fun `dedicated load cannot join occupied group`() {
        val decision = CircuitCompatibilityPolicy.evaluatePlacement(
            device = device(id = 2, dedicated = true),
            target = group(devices = listOf(device(id = 1)))
        )
        assertFalse(decision.allowed)
        assertTrue(CircuitCompatibilityPolicy.Reason.DEDICATED_LOAD_IN_SHARED_GROUP in decision.reasons)
    }

    @Test
    fun `three phase load is rejected in single phase project`() {
        val decision = CircuitCompatibilityPolicy.evaluateGroup(
            group(
                phaseMode = PhaseMode.SINGLE,
                phase = Phase.A,
                devices = listOf(
                    device(id = 1, voltageType = VoltageType.AC_3PHASE)
                )
            )
        )
        assertFalse(decision.allowed)
        assertTrue(
            CircuitCompatibilityPolicy.Reason.THREE_PHASE_LOAD_IN_SINGLE_PHASE_PROJECT in
                decision.reasons
        )
    }

    @Test
    fun `mixed semantic types remain explicit warning when electrical topology matches`() {
        val decision = CircuitCompatibilityPolicy.evaluateGroup(
            group(
                devices = listOf(
                    device(id = 1, type = DeviceType.SOCKET),
                    device(id = 2, type = DeviceType.OTHER)
                )
            )
        )
        assertTrue(decision.allowed)
        assertEquals(CircuitCompatibilityPolicy.Status.ALLOWED_WITH_WARNING, decision.status)
        assertTrue(CircuitCompatibilityPolicy.Reason.MIXED_DEVICE_TYPES in decision.reasons)
    }

    private fun device(
        id: Long,
        roomId: Long = 1,
        type: DeviceType = DeviceType.SOCKET,
        voltageType: VoltageType = VoltageType.AC_1PHASE,
        dedicated: Boolean = false,
        socket: Boolean = true
    ) = CircuitCompatibilityPolicy.DeviceInput(
        id = id,
        roomId = roomId,
        deviceType = type,
        voltageType = voltageType,
        requiresDedicatedCircuit = dedicated,
        requiresSocketConnection = socket
    )

    private fun group(
        phaseMode: PhaseMode = PhaseMode.THREE,
        phase: Phase = Phase.A,
        devices: List<CircuitCompatibilityPolicy.DeviceInput>
    ) = CircuitCompatibilityPolicy.GroupInput(
        phaseMode = phaseMode,
        phase = phase,
        roomId = 1,
        devices = devices
    )
}
