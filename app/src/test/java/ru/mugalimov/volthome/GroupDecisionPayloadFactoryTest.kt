package ru.mugalimov.volthome

import java.util.Date
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.CalculationAlgorithm
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.policy.protection.RcdSelectionReason
import ru.mugalimov.volthome.ui.screens.explication.GroupDecisionPayloadFactory
import ru.mugalimov.volthome.ui.screens.explication.sheets.DecisionToneUi
import ru.mugalimov.volthome.ui.screens.explication.sheets.RuleCheckStatusUi

class GroupDecisionPayloadFactoryTest {

    @Test
    fun `rcd explanation uses persisted selection reason`() {
        val payload = GroupDecisionPayloadFactory.rcd(
            group(rcdReason = RcdSelectionReason.GENERAL_PURPOSE_SOCKET)
        )

        assertTrue(payload.ruleChecks.any { it.detail.contains("Розетка бытовая") })
        assertTrue(payload.ruleChecks.any { it.status == RuleCheckStatusUi.MATCHED })
        assertTrue(payload.formulaLines.any { it.contains("чувствительность") })
        assertTrue(payload.sourceText.orEmpty().contains("алгоритм ${CalculationAlgorithm.VERSION}"))
    }

    @Test
    fun `plug connected appliance explains rcd on its own circuit`() {
        val applianceGroup = group(
            groupNumber = 2,
            deviceName = "Звуковая система",
            deviceType = DeviceType.OTHER,
            requiresSocketConnection = true,
            rcdReason = RcdSelectionReason.SOCKET_CONNECTED_LOAD
        )

        val payload = GroupDecisionPayloadFactory.rcd(applianceGroup)

        assertTrue(payload.ruleChecks.any {
            it.status == RuleCheckStatusUi.MATCHED &&
                it.detail.contains("Звуковая система") &&
                it.detail.contains("самой группы")
        })
        assertTrue(payload.decisionSummary?.tone == DecisionToneUi.POSITIVE)
    }

    @Test
    fun `legacy plug connected group without rcd produces explicit warning`() {
        val applianceGroup = group(
            groupNumber = 2,
            deviceName = "Звуковая система",
            deviceType = DeviceType.OTHER,
            requiresSocketConnection = true,
            rcdReason = null
        )

        val payload = GroupDecisionPayloadFactory.rcd(applianceGroup)

        assertTrue(payload.decisionSummary?.tone == DecisionToneUi.WARNING)
        assertTrue(payload.ruleChecks.any { it.status == RuleCheckStatusUi.WARNING })
        assertTrue(payload.decisionSummary?.detail.orEmpty().contains("предыдущей версии"))
    }

    @Test
    fun `breaker and cable explanations expose formula and limitations`() {
        val group = group(rcdReason = null)
        val breaker = GroupDecisionPayloadFactory.breaker(group)
        val cable = GroupDecisionPayloadFactory.cable(group)

        assertTrue(breaker.formulaLines.any { it.contains("Требуемый номинал") })
        assertTrue(breaker.limitations.any { it.contains("короткого замыкания") })
        assertTrue(cable.limitations.any { it.contains("длина") })
        assertTrue(cable.limitations.any { it.contains("падение напряжения") })
    }

    private fun group(
        rcdReason: RcdSelectionReason?,
        groupNumber: Int = 1,
        deviceName: String = "Розетка бытовая",
        deviceType: DeviceType = DeviceType.SOCKET,
        requiresSocketConnection: Boolean = false
    ): CircuitGroup {
        val device = Device(
            id = 1,
            name = deviceName,
            power = 2200,
            voltage = Voltage(230, VoltageType.AC_1PHASE),
            demandRatio = 0.75,
            roomId = 1,
            createdAt = Date(0),
            deviceType = deviceType,
            powerFactor = 0.95,
            requiresSocketConnection = requiresSocketConnection
        )
        return CircuitGroup(
            groupId = 1,
            groupNumber = groupNumber,
            roomName = "Комната",
            roomId = 1,
            groupType = deviceType,
            devices = listOf(device),
            nominalCurrent = 7.55,
            installedPowerW = 2200,
            circuitBreaker = 16,
            cableSection = 2.5,
            breakerType = "C",
            rcdRequired = rcdReason != null,
            rcdCurrent = 30,
            rcdReasonCodes = listOfNotNull(rcdReason?.name),
            calculationSource = CalculationSource.AUTO,
            algorithmVersion = CalculationAlgorithm.VERSION,
            phase = Phase.A
        )
    }
}
