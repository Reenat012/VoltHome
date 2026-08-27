package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.use_case.ProjectCoverageCalculator

class ProjectCoverageCalculatorTest {

    @Test
    fun `reports devices missing from calculated groups`() {
        val devices = listOf(device(1, "Розетка"), device(2, "Микроволновка"))

        val result = ProjectCoverageCalculator.calculate(devices, setOf(1))

        assertFalse(result.isComplete)
        assertEquals(2, result.totalDeviceCount)
        assertEquals(1, result.assignedDeviceCount)
        assertEquals(listOf(2L), result.unassignedDevices.map { it.id })
    }

    @Test
    fun `complete coverage has no warning payload`() {
        val devices = listOf(device(1, "Розетка"), device(2, "Микроволновка"))

        val result = ProjectCoverageCalculator.calculate(devices, setOf(1, 2))

        assertTrue(result.isComplete)
        assertEquals(0, result.unassignedCount)
    }

    private fun device(id: Long, name: String) = Device(
        id = id,
        name = name,
        power = 1_000,
        voltage = Voltage(220, VoltageType.AC_1PHASE),
        demandRatio = 1.0,
        roomId = 1,
        deviceType = DeviceType.SOCKET,
        powerFactor = 1.0
    )
}
