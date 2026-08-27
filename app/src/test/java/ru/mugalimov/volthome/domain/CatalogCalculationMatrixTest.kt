package ru.mugalimov.volthome.domain

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.VoltageTypeAdapter
import ru.mugalimov.volthome.domain.policy.compatibility.CircuitCompatibilityPolicy
import ru.mugalimov.volthome.domain.policy.protection.RcdDeviceInput
import ru.mugalimov.volthome.domain.policy.protection.RcdSelectionPolicy

/**
 * Контрольная матрица:
 * каждый JSON-шаблон × помещение × 1/3 фазы × общий AUTO/MANUAL policy.
 *
 * Сохранение/перезапуск отдельно покрывается Room migration/instrumentation
 * тестами, потому что это уже граница локальной БД.
 */
class CatalogCalculationMatrixTest {

    private val catalog: List<DefaultDevice> by lazy {
        val file = sequenceOf(
            File("src/main/assets/default_devices.json"),
            File("app/src/main/assets/default_devices.json")
        ).firstOrNull(File::isFile)
            ?: error("default_devices.json not found from ${File(".").absolutePath}")
        val gson = GsonBuilder()
            .registerTypeAdapter(Voltage::class.java, VoltageTypeAdapter())
            .create()
        val type = object : TypeToken<List<DefaultDevice>>() {}.type
        gson.fromJson<List<DefaultDevice>>(file.readText(), type).orEmpty()
    }

    @Test
    fun `catalog contains valid complete templates for every supported type`() {
        assertTrue(catalog.isNotEmpty())
        assertEquals(DeviceType.entries.toSet(), catalog.map { it.deviceType }.toSet())
        catalog.forEach { device ->
            assertTrue("${device.name}: power", device.power > 0)
            assertTrue("${device.name}: voltage", device.voltage.value > 0)
            assertTrue("${device.name}: demand", device.demandRatio in 0.0..1.0)
            assertTrue("${device.name}: pf", device.powerFactor in 0.1..1.0)
        }
    }

    @Test
    fun `room and phase matrix is deterministic for every catalog template`() {
        val rcdPolicy = RcdSelectionPolicy()
        catalog.forEach { device ->
            RoomType.entries.forEach { roomType ->
                val rcd = rcdPolicy.select(
                    roomType = roomType,
                    devices = listOf(
                        RcdDeviceInput(
                            deviceType = device.deviceType,
                            requiresSocketConnection = device.requiresSocketConnection
                        )
                    )
                )
                val expectedRcd =
                    roomType != RoomType.STANDARD ||
                        device.requiresSocketConnection ||
                        (device.deviceType == DeviceType.SOCKET &&
                            !device.requiresSocketConnection)
                assertEquals("${device.name} / $roomType", expectedRcd, rcd.required)

                PhaseMode.entries.forEach { mode ->
                    val phase = if (device.voltage.type == VoltageType.AC_3PHASE) {
                        Phase.THREE_PHASE
                    } else {
                        Phase.A
                    }
                    val decision = CircuitCompatibilityPolicy.evaluateGroup(
                        CircuitCompatibilityPolicy.GroupInput(
                            phaseMode = mode,
                            phase = phase,
                            roomId = 1,
                            devices = listOf(
                                CircuitCompatibilityPolicy.DeviceInput(
                                    id = device.id,
                                    roomId = 1,
                                    deviceType = device.deviceType,
                                    voltageType = device.voltage.type,
                                    requiresDedicatedCircuit = device.requiresDedicatedCircuit,
                                    requiresSocketConnection = device.requiresSocketConnection
                                )
                            )
                        )
                    )
                    if (mode == PhaseMode.SINGLE &&
                        device.voltage.type == VoltageType.AC_3PHASE
                    ) {
                        assertFalse("${device.name} must be rejected in SINGLE", decision.allowed)
                    } else {
                        assertTrue("${device.name} / $mode", decision.allowed)
                    }
                }
            }
        }
    }
}
