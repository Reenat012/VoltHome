package ru.mugalimov.volthome

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.core.validation.PowerValidator
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.policy.line.LinePolicyInput
import ru.mugalimov.volthome.domain.policy.line.LinePolicySelector
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator

class HighPowerDeviceSupportTest {

    @Test
    fun `power validation accepts values above old five kilowatt limit`() {
        listOf(5_001, 7_500, 12_500, 25_000, 100_000).forEach { watts ->
            assertTrue("$watts W must be accepted", PowerValidator.validatePower(watts))
            assertNull(PowerValidator.errorMessage(watts))
        }
    }

    @Test
    fun `single and three phase high power currents keep correct formulas`() {
        val single = CurrentCalculator.calculateCalculatedCurrent(
            power = 12_500.0,
            voltage = 230.0,
            powerFactor = 1.0,
            demandRatio = 1.0,
            voltageType = VoltageType.AC_1PHASE
        )
        val three = CurrentCalculator.calculateCalculatedCurrent(
            power = 12_500.0,
            voltage = 400.0,
            powerFactor = 1.0,
            demandRatio = 1.0,
            voltageType = VoltageType.AC_3PHASE
        )

        assertEquals(12_500.0 / 230.0, single, 1e-9)
        assertEquals(12_500.0 / (sqrt(3.0) * 400.0), three, 1e-9)
    }

    @Test
    fun `line policy supports protection matrix through one hundred sixty amps`() {
        val selector = LinePolicySelector()
        val expected = listOf(
            18.0 to (20 to 2.5),
            54.0 to (63 to 16.0),
            79.0 to (80 to 25.0),
            99.0 to (100 to 35.0),
            124.0 to (125 to 50.0),
            159.0 to (160 to 70.0)
        )

        expected.forEach { (current, expectedLine) ->
            val profile = selector.select(
                LinePolicyInput(
                    nominalCurrentA = current,
                    deviceType = DeviceType.HEAVY_DUTY,
                    hasMotor = false
                )
            ).profile

            assertEquals("breaker for $current A", expectedLine.first, profile.breakerRating)
            assertEquals("cable for $current A", expectedLine.second, profile.cableSection, 0.0)
        }
    }
}
