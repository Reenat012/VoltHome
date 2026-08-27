package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.cable.CableCalculationStatus
import ru.mugalimov.volthome.domain.model.cable.CableInsulation
import ru.mugalimov.volthome.domain.model.cable.CableInstallationMethod
import ru.mugalimov.volthome.domain.model.cable.CableLineInput
import ru.mugalimov.volthome.domain.model.cable.ConductorMaterial
import ru.mugalimov.volthome.domain.model.cable.ProjectCableDefaults
import ru.mugalimov.volthome.domain.use_case.cable.CableLineCalculator

class CableLineCalculatorTest {
    private val calculator = CableLineCalculator()

    @Test
    fun lineWithoutLengthRemainsPreliminary() {
        val result = calculator.calculate(input(lengthM = null))

        assertEquals(CableCalculationStatus.PRELIMINARY, result.status)
        assertNull(result.voltageDropPercent)
        assertEquals(3, result.cable.cores)
    }

    @Test
    fun singlePhaseLineSelectsSectionByBreakerAndVoltageDrop() {
        val result = calculator.calculate(input(lengthM = 20.0))

        assertEquals(CableCalculationStatus.PASSED, result.status)
        assertEquals(2.5, result.cable.phaseSectionMm2, 0.0)
        assertEquals(3, result.cable.cores)
        assertTrue(result.voltageDropPercent!! <= 3.0)
    }

    @Test
    fun threePhaseLineUsesFiveCoresAnd400VoltDropFormula() {
        val single = calculator.calculate(input(lengthM = 30.0, phaseMode = PhaseMode.SINGLE))
        val three = calculator.calculate(input(lengthM = 30.0, phaseMode = PhaseMode.THREE))

        assertEquals(5, three.cable.cores)
        assertTrue(three.voltageDropPercent!! < single.voltageDropPercent!!)
    }

    @Test
    fun temperatureAndGroupingDeratingIncreaseRequiredSection() {
        val normal = calculator.calculate(input(lengthM = 10.0))
        val derated = calculator.calculate(
            input(
                lengthM = 10.0,
                defaults = defaults().copy(ambientTemperatureC = 50, groupedCircuits = 3)
            )
        )

        assertEquals(2.5, normal.cable.phaseSectionMm2, 0.0)
        assertEquals(6.0, derated.cable.phaseSectionMm2, 0.0)
    }

    @Test
    fun intermediatePvcTemperatureUsesNextHotterTableRow() {
        val result = calculator.calculate(
            input(
                lengthM = 10.0,
                defaults = defaults().copy(ambientTemperatureC = 32)
            )
        )

        assertEquals(0.94, result.temperatureFactor, 0.0001)
    }

    @Test
    fun intermediateXlpeTemperatureUsesNextHotterTableRow() {
        val result = calculator.calculate(
            input(
                lengthM = 10.0,
                defaults = defaults().copy(
                    insulation = CableInsulation.XLPE_90,
                    ambientTemperatureC = 67
                )
            )
        )

        assertEquals(0.58, result.temperatureFactor, 0.0001)
    }

    @Test
    fun temperatureBelowTableUsesConservativeLowerBoundary() {
        val result = calculator.calculate(
            input(
                lengthM = 10.0,
                defaults = defaults().copy(ambientTemperatureC = -25)
            )
        )

        assertEquals(1.22, result.temperatureFactor, 0.0001)
    }

    @Test
    fun temperatureAboveInsulationTableMakesAmpacityCheckFail() {
        val result = calculator.calculate(
            input(
                lengthM = 10.0,
                defaults = defaults().copy(ambientTemperatureC = 65)
            )
        )

        assertEquals(0.0, result.temperatureFactor, 0.0)
        assertEquals(CableCalculationStatus.FAILED, result.status)
    }

    @Test
    fun undersizedManualSectionFailsAmpacityCheck() {
        val result = calculator.calculate(input(lengthM = 10.0, manualSectionMm2 = 1.5))

        assertEquals(CableCalculationStatus.FAILED, result.status)
        assertEquals(1.5, result.cable.phaseSectionMm2, 0.0)
    }

    @Test
    fun nonStandardManualSectionNeverBorrowsAmpacityFromLargestCable() {
        val result = calculator.calculate(input(lengthM = 10.0, manualSectionMm2 = 1.0))

        assertEquals(CableCalculationStatus.FAILED, result.status)
        assertEquals(0.0, result.correctedAmpacityA, 0.0)
    }

    @Test
    fun breakerSmallerThanLoadDoesNotForceMaximumCableSection() {
        val result = calculator.calculate(input(lengthM = 10.0, loadCurrentA = 20.0, breakerA = 16))

        assertEquals(CableCalculationStatus.FAILED, result.status)
        assertEquals(2.5, result.cable.phaseSectionMm2, 0.0)
    }

    private fun input(
        lengthM: Double?,
        phaseMode: PhaseMode = PhaseMode.SINGLE,
        loadCurrentA: Double = 10.0,
        breakerA: Int = 16,
        defaults: ProjectCableDefaults = defaults(),
        manualSectionMm2: Double? = null
    ) = CableLineInput(
        projectId = "p1",
        groupId = 1L,
        phaseMode = phaseMode,
        loadCurrentA = loadCurrentA,
        breakerA = breakerA,
        lengthM = lengthM,
        powerFactor = 0.95,
        defaults = defaults,
        manualSectionMm2 = manualSectionMm2,
        legacySectionMm2 = 2.5
    )

    private fun defaults() = ProjectCableDefaults(
        projectId = "p1",
        material = ConductorMaterial.COPPER,
        insulation = CableInsulation.PVC_70,
        installationMethod = CableInstallationMethod.CONDUIT_WALL,
        ambientTemperatureC = 25,
        groupedCircuits = 1,
        maxVoltageDropPercent = 3.0
    )
}
