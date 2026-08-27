package ru.mugalimov.volthome.ui.screens.explication.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.mugalimov.volthome.domain.model.CalcInput
import ru.mugalimov.volthome.domain.model.CalcOutput
import ru.mugalimov.volthome.domain.model.CalcStep

class CalculationStoryMapperTest {

    @Test
    fun `group current exposes base factor and calculated contribution`() {
        val story = CalculationStoryMapper.mapGroup(
            steps = listOf(
                CalcStep(
                    name = "Ток группы",
                    formula = "Iгр(расч) = Σ Iрасч,i",
                    inputs = listOf(
                        CalcInput("deviceId=7;label=Розетка бытовая / base", 11.25, "А"),
                        CalcInput("deviceId=7;label=Розетка бытовая / k", 0.75, ""),
                        CalcInput("deviceId=7;label=Розетка бытовая / result", 8.4375, "А")
                    ),
                    output = CalcOutput(8.4375, "А")
                )
            ),
            kind = GroupCalculationKind.CURRENT
        )

        assertNotNull(story)
        assertEquals("8,44 А", story?.resultText)
        assertEquals("11,25 А", story?.comparisonText)
        assertEquals("Коэффициенты спроса снизили учитываемую нагрузку на 25%.", story?.impactText)
        assertEquals(1, story?.contributions?.size)
        assertEquals("Розетка бытовая", story?.contributions?.single()?.label)
        assertEquals("× 0,75", story?.contributions?.single()?.factorText)
        assertEquals("8,44 А", story?.contributions?.single()?.resultText)
    }

    @Test
    fun `group power adapts watts to kilowatts`() {
        val story = CalculationStoryMapper.mapGroup(
            steps = listOf(
                CalcStep(
                    name = "Мощность группы",
                    formula = "Pгр(расч) = Σ (Pуст,i × kспроса,i)",
                    inputs = listOf(
                        CalcInput("Микроволновая печь / base", 2200.0, "Вт"),
                        CalcInput("Микроволновая печь / k", 0.8, ""),
                        CalcInput("Микроволновая печь / result", 1760.0, "Вт")
                    ),
                    output = CalcOutput(1760.0, "Вт")
                )
            ),
            kind = GroupCalculationKind.POWER
        )

        assertEquals("1,76 кВт", story?.resultText)
        assertEquals("2,2 кВт", story?.comparisonText)
        assertEquals("2,2 кВт", story?.contributions?.single()?.inputText)
        assertEquals("1,76 кВт", story?.contributions?.single()?.resultText)
    }

    @Test
    fun `mapper does not infer missing demand factor`() {
        val story = CalculationStoryMapper.mapGroup(
            steps = listOf(
                CalcStep(
                    name = "Старый расчёт",
                    formula = "Iгр(расч) = Σ Iрасч,i",
                    inputs = listOf(CalcInput("Устройство", 10.0, "А")),
                    output = CalcOutput(7.5, "А")
                )
            ),
            kind = GroupCalculationKind.CURRENT
        )

        assertNull(story)
    }

    @Test
    fun `multiple steps are not merged into misleading story`() {
        val step = CalcStep(
            name = "Шаг",
            formula = "x",
            inputs = listOf(CalcInput("Устройство / base", 1.0, "А")),
            output = CalcOutput(1.0, "А")
        )

        assertNull(
            CalculationStoryMapper.mapGroup(
                steps = listOf(step, step),
                kind = GroupCalculationKind.CURRENT
            )
        )
    }

    @Test
    fun `installed power presents every passport value without demand factor`() {
        val story = CalculationStoryMapper.mapInstalledPower(
            steps = listOf(
                CalcStep(
                    name = "Установленная мощность",
                    formula = "Pуст(щит) = Σ Pуст,i",
                    inputs = listOf(
                        CalcInput("Розетка", 2200.0, "Вт"),
                        CalcInput("Освещение", 500.0, "Вт")
                    ),
                    output = CalcOutput(2700.0, "Вт")
                )
            )
        )

        assertEquals("2,7 кВт", story?.resultText)
        assertEquals(2, story?.contributions?.size)
        assertEquals("Сумма паспортных мощностей: 2 устройства.", story?.impactText)
        assertNull(story?.contributions?.first()?.factorText)
    }
}
