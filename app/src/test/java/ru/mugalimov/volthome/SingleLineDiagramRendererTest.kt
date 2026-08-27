package ru.mugalimov.volthome

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.singleline.SingleLineBus
import ru.mugalimov.volthome.domain.model.singleline.SingleLineBusType
import ru.mugalimov.volthome.domain.model.singleline.SingleLineDeviceSummary
import ru.mugalimov.volthome.domain.model.singleline.SingleLineDiagram
import ru.mugalimov.volthome.domain.model.singleline.SingleLineGroupBlock
import ru.mugalimov.volthome.domain.model.singleline.SingleLineInputBlock
import ru.mugalimov.volthome.domain.model.singleline.SingleLinePhaseSection
import ru.mugalimov.volthome.domain.model.singleline.SingleLineProtectionBlock
import ru.mugalimov.volthome.domain.model.singleline.SingleLineProtectionType
import ru.mugalimov.volthome.ui.screens.explication.export_pdf.SingleLineDiagramRenderer

class SingleLineDiagramRendererTest {

    @Test
    fun `renderer uses conductors buses and apparatus symbols`() {
        val html = SingleLineDiagramRenderer.render(sampleDiagram())

        assertTrue(html.contains("distribution-bar"))
        assertTrue(html.contains("phase-bus-line"))
        assertTrue(html.contains("feeder-drop"))
        assertTrue(html.contains("<svg class=\"device-symbol\""))
        assertFalse(html.contains("class=\"device-node\""))
        assertFalse(html.contains("class=\"small-node\""))
        assertFalse(html.contains("class=\"load-node\""))

        System.getenv("SINGLE_LINE_PREVIEW_PATH")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).writeText(html) }
    }

    @Test
    fun `input leakage value is not duplicated`() {
        val html = SingleLineDiagramRenderer.render(sampleDiagram())

        assertTrue(html.contains("C20 / 300мА"))
        assertFalse(html.contains("C20 / 300мА / 300 мА"))
    }

    private fun sampleDiagram(): SingleLineDiagram {
        val sections = listOf(Phase.A, Phase.B, Phase.C).mapIndexed { phaseIndex, phase ->
            SingleLinePhaseSection(
                phase = phase,
                phaseBus = SingleLineBus(
                    type = SingleLineBusType.PHASE,
                    label = "Фазная шина ${phase.name}",
                    phase = phase
                ),
                totalCurrentAmps = 12.4 + phaseIndex,
                totalInstalledPowerWatts = 2400.0 + phaseIndex * 300.0,
                totalCalculatedPowerWatts = null,
                groups = (1..2).map { itemIndex ->
                    val groupNumber = phaseIndex * 2 + itemIndex
                    sampleGroup(
                        number = groupNumber,
                        phase = phase,
                        hasRcd = itemIndex == 1
                    )
                }
            )
        }

        return SingleLineDiagram(
            projectName = "Проект №1",
            phaseMode = PhaseMode.THREE,
            input = SingleLineInputBlock(
                title = "Ввод 3ф",
                incomerLabel = "C20",
                incomerNominalCurrentLabel = "20",
                totalInstalledPowerWatts = 8100.0,
                totalCalculatedPowerWatts = 6900.0,
                totalCurrentAmps = 39.4
            ),
            protectionBlocks = listOf(
                SingleLineProtectionBlock(
                    id = "input_breaker",
                    title = "Вводной автомат",
                    type = SingleLineProtectionType.INPUT_BREAKER,
                    phase = null,
                    nominalCurrentAmps = 20.0,
                    leakageCurrentMilliAmps = 300,
                    description = "C20 / 300мА"
                )
            ),
            phaseSections = sections,
            neutralBus = SingleLineBus(SingleLineBusType.NEUTRAL, "N"),
            protectiveEarthBus = SingleLineBus(SingleLineBusType.PROTECTIVE_EARTH, "PE"),
            generatedAtMillis = 0L
        )
    }

    private fun sampleGroup(
        number: Int,
        phase: Phase,
        hasRcd: Boolean
    ): SingleLineGroupBlock {
        return SingleLineGroupBlock(
            groupId = number.toLong(),
            groupNumber = number,
            groupName = if (number % 2 == 0) "LIGHTING" else "SOCKET",
            phase = phase,
            roomNames = listOf(if (number % 2 == 0) "Спальня" else "Кухня"),
            devices = listOf(
                SingleLineDeviceSummary(
                    deviceId = number.toLong(),
                    name = if (number % 2 == 0) "Освещение" else "Розетки",
                    roomName = null,
                    type = null,
                    powerWatts = 1200.0,
                    calculatedPowerWatts = null,
                    calculatedCurrentAmps = 5.4
                )
            ),
            installedPowerWatts = 1200.0,
            calculatedPowerWatts = null,
            calculatedCurrentAmps = 5.4,
            breakerLabel = "C16",
            cableLabel = "3×2.5",
            rcdLabel = if (hasRcd) "УЗО 30мА" else null,
            leakageCurrentMilliAmps = if (hasRcd) 30 else null,
            warnings = emptyList()
        )
    }
}
