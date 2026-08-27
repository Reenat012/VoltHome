package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.use_case.ProtectionDeviceInventory

class ProtectionDeviceInventorySlotTest {

    @Test
    fun `slot survives auto rebuild that changes room group primary key`() {
        val before = group(groupId = 10, devices = listOf(device(100), device(101)))
        val after = before.copy(groupId = 999, groupNumber = 7)

        assertEquals(
            ProtectionDeviceInventory.groupSlotPrefix(before),
            ProtectionDeviceInventory.groupSlotPrefix(after)
        )
    }

    @Test
    fun `changed circuit composition receives another slot`() {
        val before = group(groupId = 10, devices = listOf(device(100)))
        val after = before.copy(devices = listOf(device(100), device(101)))

        assertNotEquals(
            ProtectionDeviceInventory.groupSlotPrefix(before),
            ProtectionDeviceInventory.groupSlotPrefix(after)
        )
    }

    private fun group(groupId: Long, devices: List<Device>) = CircuitGroup(
        groupId = groupId,
        groupNumber = 1,
        roomName = "Комната",
        roomId = 5,
        groupType = DeviceType.SOCKET,
        devices = devices,
        nominalCurrent = 8.0,
        installedPowerW = 1_800,
        circuitBreaker = 16,
        cableSection = 2.5,
        breakerType = "C",
        rcdRequired = false,
        phase = Phase.A
    )

    private fun device(id: Long) = Device(
        id = id,
        name = "Устройство $id",
        power = 100,
        voltage = Voltage(220, VoltageType.AC_1PHASE),
        demandRatio = 1.0,
        roomId = 5,
        deviceType = DeviceType.SOCKET,
        powerFactor = 1.0
    )
}
