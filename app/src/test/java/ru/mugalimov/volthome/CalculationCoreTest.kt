package ru.mugalimov.volthome

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.incomer.IncomerAssessmentStatus
import ru.mugalimov.volthome.domain.model.incomer.IncomerIssue
import ru.mugalimov.volthome.domain.model.manual.ManualDeviceDraft
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.policy.protection.RcdSelectionReason
import ru.mugalimov.volthome.domain.policy.line.LinePolicyInput
import ru.mugalimov.volthome.domain.policy.line.LinePolicySelector
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator
import ru.mugalimov.volthome.domain.use_case.CircuitLoadCalculator
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import ru.mugalimov.volthome.domain.use_case.inferVoltageType
import ru.mugalimov.volthome.domain.use_case.manual.ManualDraftSelectors
import ru.mugalimov.volthome.domain.use_case.manual.RecalculateGroupLineUseCase
import ru.mugalimov.volthome.domain.use_case.phaseLoadVector
import ru.mugalimov.volthome.domain.use_case.phase_load.PhaseLoadItemsBuilder

class CalculationCoreTest {

    @Test
    fun threePhaseFormulaReturnsLineCurrent() {
        val current = CurrentCalculator.calculateCalculatedCurrent(
            power = 7_000.0,
            voltage = 380.0,
            powerFactor = 1.0,
            demandRatio = 1.0,
            voltageType = VoltageType.AC_3PHASE
        )

        assertEquals(7_000.0 / (sqrt(3.0) * 380.0), current, 1e-9)
    }

    @Test
    fun threePhaseGroupContributesFullLineCurrentToEveryPhase() {
        val vector = phaseLoadVector(
            listOf(group(current = 10.63, phase = Phase.THREE_PHASE))
        )

        assertEquals(10.63, vector.a, 1e-9)
        assertEquals(10.63, vector.b, 1e-9)
        assertEquals(10.63, vector.c, 1e-9)
    }

    @Test
    fun phaseLoadUiDoesNotDivideThreePhaseLineCurrentByThree() {
        val device = Device(
            id = 1L,
            name = "Плита",
            power = 7_000,
            voltage = Voltage(380, VoltageType.AC_3PHASE),
            demandRatio = 1.0,
            roomId = 1L,
            deviceType = DeviceType.ELECTRIC_STOVE,
            powerFactor = 1.0,
            requiresDedicatedCircuit = true,
            requiresSocketConnection = false
        )
        val lineCurrent = CurrentCalculator.calculateCalculatedCurrent(
            power = 7_000.0,
            voltage = 380.0,
            powerFactor = 1.0,
            demandRatio = 1.0,
            voltageType = VoltageType.AC_3PHASE
        )
        val items = PhaseLoadItemsBuilder.build(
            listOf(
                group(current = lineCurrent, phase = Phase.THREE_PHASE).copy(
                    groupType = DeviceType.ELECTRIC_STOVE,
                    devices = listOf(device),
                    installedPowerW = 7_000,
                    circuitBreaker = 25,
                    cableSection = 4.0
                )
            )
        )

        assertEquals(lineCurrent, items.first { it.phase == Phase.A }.totalCurrent, 1e-9)
        assertEquals(lineCurrent, items.first { it.phase == Phase.B }.totalCurrent, 1e-9)
        assertEquals(lineCurrent, items.first { it.phase == Phase.C }.totalCurrent, 1e-9)
    }

    @Test
    fun installedAndCalculatedCurrentsHaveDifferentSemantics() {
        val load = CurrentCalculator.calculateDeviceLoad(
            ru.mugalimov.volthome.domain.use_case.LoadInput(
                powerW = 2_300.0,
                voltage = 230.0,
                powerFactor = 1.0,
                demandRatio = 0.5,
                voltageType = VoltageType.AC_1PHASE
            )
        )

        assertEquals(10.0, load.installedCurrentA, 1e-9)
        assertEquals(5.0, load.calculatedCurrentA, 1e-9)
    }

    @Test
    fun manualCalculationUsesActualDeviceVoltage() {
        val device = ManualDeviceDraft(
            deviceId = 1L,
            roomId = 1L,
            roomName = "Test",
            deviceType = DeviceType.OTHER,
            powerW = 1_000,
            voltageType = VoltageType.AC_1PHASE,
            voltageValue = 200,
            demandRatio = 1.0,
            powerFactor = 1.0,
            hasMotor = false,
            requiresDedicatedCircuit = false,
            requiresSocketConnection = false
        )

        assertEquals(5.0, ManualDraftSelectors.calcDeviceCurrentA(device), 1e-9)
    }

    @Test
    fun manualRecalculationAddsRcdToSocketConnectedApplianceGroup() {
        val device = ManualDeviceDraft(
            deviceId = 1L,
            roomId = 1L,
            roomName = "Комната",
            deviceType = DeviceType.SOCKET,
            powerW = 1_000,
            voltageType = VoltageType.AC_1PHASE,
            voltageValue = 230,
            demandRatio = 0.8,
            powerFactor = 0.85,
            hasMotor = false,
            requiresDedicatedCircuit = false,
            requiresSocketConnection = true
        )
        val group = ManualGroupDraft(
            groupId = 3L,
            groupNumber = 3,
            roomId = 1L,
            roomName = "Комната",
            groupType = DeviceType.SOCKET,
            phase = Phase.A,
            deviceIds = listOf(device.deviceId),
            rcdRequired = false,
            rcdCurrent = 30
        )

        val recalculated = RecalculateGroupLineUseCase().execute(
            RecalculateGroupLineUseCase.Params(
                group = group,
                devicesInGroup = listOf(device)
            )
        )

        assertEquals(true, recalculated.rcdRequired)
        assertEquals(30, recalculated.rcdCurrent)
        assertEquals(
            listOf(RcdSelectionReason.SOCKET_CONNECTED_LOAD),
            recalculated.rcdReasons
        )
    }

    @Test
    fun manualRecalculationAllowsMixedVoltageAsExplicitDeviation() {
        val onePhase = ManualDeviceDraft(
            deviceId = 1L,
            roomId = 1L,
            roomName = "Комната",
            deviceType = DeviceType.SOCKET,
            powerW = 1_000,
            voltageType = VoltageType.AC_1PHASE,
            voltageValue = 230,
            demandRatio = 1.0,
            powerFactor = 1.0,
            hasMotor = false,
            requiresDedicatedCircuit = false,
            requiresSocketConnection = true
        )
        val threePhase = onePhase.copy(
            deviceId = 2L,
            voltageType = VoltageType.AC_3PHASE,
            voltageValue = 400
        )
        val group = ManualGroupDraft(
            groupId = 1L,
            groupNumber = 1,
            roomId = 1L,
            roomName = "Комната",
            groupType = DeviceType.SOCKET,
            phase = Phase.A,
            deviceIds = listOf(1L, 2L)
        )

        val result = RecalculateGroupLineUseCase().execute(
            RecalculateGroupLineUseCase.Params(group, listOf(onePhase, threePhase))
        )

        assertTrue(result.nominalCurrent != null && result.nominalCurrent!! > 0.0)
        assertTrue(result.circuitBreaker != null)
    }

    @Test
    fun highRatedNonMotorBreakerDoesNotReceiveCurveDByRatingAlone() {
        val profile = LinePolicySelector().select(
            LinePolicyInput(
                nominalCurrentA = 51.0,
                deviceType = DeviceType.OTHER,
                hasMotor = false
            )
        ).profile

        assertEquals(63, profile.breakerRating)
        assertEquals("C", profile.breakerType)
    }

    @Test
    fun projectWithOnlyThreePhaseGroupIsDetectedAsThreePhase() {
        val groups = listOf(group(current = 10.63, phase = Phase.THREE_PHASE))

        assertEquals(VoltageType.AC_3PHASE, inferVoltageType(groups))
        val incomer = IncomerSelector().select(IncomerSelector.Params(groups = groups))
        assertEquals(4, incomer.poles)
        assertEquals(16, incomer.mcbRating)
    }

    @Test
    fun incomerOverflowIsAnExplicitUnsupportedAssessment() {
        val result = IncomerSelector().assess(
            IncomerSelector.Params(
                groups = listOf(
                    group(current = 200.0, phase = Phase.A).copy(circuitBreaker = 250)
                ),
                voltageTypeOverride = VoltageType.AC_1PHASE
            )
        )

        assertEquals(IncomerAssessmentStatus.REQUIRED_RATING_UNSUPPORTED, result.status)
        assertEquals(null, result.requiredMcbRatingA)
        assertTrue(IncomerIssue.REQUIRED_RATING_UNSUPPORTED in result.issues)
    }

    @Test
    fun knownAvailablePowerLimitsProposedSinglePhaseRatingAndReportsConflict() {
        val result = IncomerSelector().assess(
            IncomerSelector.Params(
                groups = listOf(
                    group(current = 110.0, phase = Phase.A).copy(circuitBreaker = 160)
                ),
                availablePowerKw = 15.0,
                voltageTypeOverride = VoltageType.AC_1PHASE
            )
        )

        assertEquals(160, result.requiredMcbRatingA)
        assertEquals(63, result.permittedMcbRatingA)
        assertEquals(63, result.spec.mcbRating)
        assertEquals(IncomerAssessmentStatus.LOAD_EXCEEDS_AVAILABLE_POWER, result.status)
    }

    @Test
    fun threePhaseAvailablePowerUsesLineCurrentInsteadOfSinglePhaseFormula() {
        val result = IncomerSelector().assess(
            IncomerSelector.Params(
                groups = listOf(group(current = 12.0, phase = Phase.THREE_PHASE)),
                availablePowerKw = 15.0,
                voltageTypeOverride = VoltageType.AC_3PHASE
            )
        )

        assertEquals(16, result.requiredMcbRatingA)
        assertEquals(20, result.permittedMcbRatingA)
        assertEquals(16, result.spec.mcbRating)
        assertEquals(IncomerAssessmentStatus.WITHIN_AVAILABLE_POWER, result.status)
    }

    @Test
    fun unassignedDevicesMakeIncomerAssessmentIncomplete() {
        val result = IncomerSelector().assess(
            IncomerSelector.Params(
                groups = listOf(group(current = 8.0, phase = Phase.A)),
                availablePowerKw = 10.0,
                unassignedDeviceCount = 2,
                voltageTypeOverride = VoltageType.AC_1PHASE
            )
        )

        assertEquals(IncomerAssessmentStatus.INCOMPLETE_PROJECT, result.status)
        assertEquals(2, result.unassignedDeviceCount)
        assertTrue(IncomerIssue.UNASSIGNED_DEVICES in result.issues)
    }

    @Test
    fun projectWithoutCalculatedGroupsCannotProduceConclusiveIncomer() {
        val result = IncomerSelector().assess(
            IncomerSelector.Params(
                groups = emptyList(),
                availablePowerKw = 15.0,
                voltageTypeOverride = VoltageType.AC_1PHASE
            )
        )

        assertEquals(IncomerAssessmentStatus.INCOMPLETE_PROJECT, result.status)
        assertTrue(IncomerIssue.NO_CALCULATED_GROUPS in result.issues)
    }

    @Test
    fun multipleGeneralSocketPointsInOneRoomUseSingleLineAllowance() {
        val onePoint = generalSocket(id = 1L, roomId = 10L)
        val fivePoints = (1L..5L).map { generalSocket(id = it, roomId = 10L) }

        val singleLoad = CircuitLoadCalculator.calculate(listOf(onePoint))
        val repeatedLoad = CircuitLoadCalculator.calculate(fivePoints)

        assertEquals(singleLoad.installedPowerW, repeatedLoad.installedPowerW, 1e-9)
        assertEquals(singleLoad.calculatedPowerW, repeatedLoad.calculatedPowerW, 1e-9)
        assertEquals(singleLoad.calculatedCurrentA, repeatedLoad.calculatedCurrentA, 1e-9)
    }

    @Test
    fun generalSocketAllowancesRemainSeparateBetweenRooms() {
        val first = generalSocket(id = 1L, roomId = 10L)
        val second = generalSocket(id = 2L, roomId = 20L)
        val oneRoomLoad = CircuitLoadCalculator.calculate(listOf(first))
        val twoRoomLoad = CircuitLoadCalculator.calculate(listOf(first, second))

        assertEquals(oneRoomLoad.calculatedPowerW * 2.0, twoRoomLoad.calculatedPowerW, 1e-9)
        assertEquals(oneRoomLoad.calculatedCurrentA * 2.0, twoRoomLoad.calculatedCurrentA, 1e-9)
    }

    @Test
    fun realPlugConnectedAppliancesAreStillSummed() {
        val microwave = generalSocket(id = 1L, roomId = 10L).copy(
            name = "Микроволновая печь",
            power = 1_200,
            deviceType = DeviceType.OTHER,
            requiresSocketConnection = true
        )
        val kettle = microwave.copy(id = 2L, name = "Чайник", power = 2_000)

        val combined = CircuitLoadCalculator.calculate(listOf(microwave, kettle))
        val microwaveOnly = CircuitLoadCalculator.calculate(listOf(microwave))
        val kettleOnly = CircuitLoadCalculator.calculate(listOf(kettle))

        assertEquals(
            microwaveOnly.calculatedPowerW + kettleOnly.calculatedPowerW,
            combined.calculatedPowerW,
            1e-9
        )
    }

    @Test
    fun invalidCoefficientsAreRejectedByCore() {
        try {
            CurrentCalculator.calculateCalculatedCurrent(
                power = 1_000.0,
                voltage = 230.0,
                powerFactor = Double.NaN,
                demandRatio = 1.0,
                voltageType = VoltageType.AC_1PHASE
            )
            fail("Expected invalid power factor error")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    private fun group(current: Double, phase: Phase): CircuitGroup = CircuitGroup(
        groupId = phase.ordinal.toLong() + 1L,
        groupNumber = phase.ordinal + 1,
        roomName = "Test",
        roomId = 1L,
        groupType = DeviceType.SOCKET,
        devices = emptyList(),
        nominalCurrent = current,
        installedPowerW = 0,
        circuitBreaker = 16,
        cableSection = 2.5,
        breakerType = "C",
        rcdRequired = false,
        phase = phase
    )

    private fun generalSocket(id: Long, roomId: Long): Device = Device(
        id = id,
        name = "Розетка бытовая",
        power = 2_200,
        voltage = Voltage(230, VoltageType.AC_1PHASE),
        demandRatio = 0.75,
        roomId = roomId,
        deviceType = DeviceType.SOCKET,
        powerFactor = 0.95,
        requiresDedicatedCircuit = false,
        requiresSocketConnection = false
    )
}
