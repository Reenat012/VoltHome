package ru.mugalimov.volthome

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcInput
import ru.mugalimov.volthome.domain.model.CalcOutput
import ru.mugalimov.volthome.domain.model.CalcStep
import ru.mugalimov.volthome.domain.model.CoefficientSource
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.report.DonutModel
import ru.mugalimov.volthome.domain.model.report.ReportDevice
import ru.mugalimov.volthome.domain.model.report.ReportGroup
import ru.mugalimov.volthome.domain.model.report.ReportModel
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import ru.mugalimov.volthome.domain.model.report.professional.ProfessionalSections
import ru.mugalimov.volthome.domain.model.report.professional.ReportNormRefItem
import ru.mugalimov.volthome.domain.model.report.professional.ReportWarningItem
import ru.mugalimov.volthome.ui.utilities.HtmlReportBuilder

class HtmlReportBuilderTest {

    @Test
    fun `pro report renders customer sections without calculation log tokens`() {
        val html = render(sampleModel(groupsPerPhase = 4))

        assertTrue(html.contains("Проект №1"))
        assertTrue(html.contains("Ключевые показатели"))
        assertTrue(html.contains("Экспликация групп"))
        assertTrue(html.contains("Как сформирован результат"))
        assertTrue(html.contains("Принятые инженерные допущения"))
        assertTrue(html.contains("Нормативная база"))
        assertTrue(html.contains("Выбор аппаратов и распределение"))
        assertTrue(html.contains("Границы расчётной модели"))
        assertTrue(html.contains("устройства подключаются через розетку"))
        assertTrue(html.contains("длина и материал кабеля"))
        assertTrue(html.contains("Есть замечания"))
        assertTrue(html.contains("Автомат C16 + УЗО 30 мА"))

        listOf(
            "deviceId=",
            " / base",
            " / k",
            " / result",
            "OTHER · USER",
            "Титул",
            "Подстановка"
        ).forEach { forbidden ->
            assertFalse("Report contains technical token: $forbidden", html.contains(forbidden))
        }

        // Для визуальной проверки формируем типичный проект: по две группы на фазу.
        writePreviewIfRequested(render(sampleModel(groupsPerPhase = 2)))
    }

    @Test
    fun `free report keeps summary and hides pro page through profile class`() {
        val html = render(
            sampleModel(groupsPerPhase = 1).copy(
                profile = ReportModel.ReportProfile.FREE,
                professional = null,
                steps = emptyList(),
                assumptions = emptyList()
            )
        )

        assertTrue(html.contains("class=\"with-watermark vh vh-free\"").not())
        assertTrue(html.contains("vh-free"))
        assertTrue(html.contains("Ключевые показатели"))
        assertFalse(html.contains("deviceId="))
    }

    private fun render(model: ReportModel): String {
        val template = templateFile().readText()
        return HtmlReportBuilder(template).build(model)
    }

    private fun writePreviewIfRequested(html: String) {
        val destination = System.getenv("REPORT_PREVIEW_PATH")
            ?.takeIf { it.isNotBlank() }
            ?: return
        val logo = File(templateFile().parentFile, "img/logo.png").absoluteFile
        val portableHtml = html.replace(
            "src=\"img/logo.png\"",
            "src=\"file://${logo.path}\""
        )
        File(destination).writeText(portableHtml)
    }

    private fun templateFile(): File {
        return sequenceOf(
            File("app/src/main/assets/report_pdf/template.html"),
            File("src/main/assets/report_pdf/template.html")
        ).first { it.exists() }
    }

    private fun sampleModel(groupsPerPhase: Int): ReportModel {
        val phases = listOf(Phase.A, Phase.B, Phase.C).mapIndexed { phaseIndex, phase ->
            val groups = (1..groupsPerPhase).map { localIndex ->
                val number = phaseIndex * groupsPerPhase + localIndex
                sampleGroup(number = number, phase = phase, withRcd = localIndex % 2 == 0)
            }
            ReportPhase(
                name = "Фаза ${phase.name}",
                groups = groups,
                totalCurrentA = groups.sumOf { it.calculatedCurrentA ?: 0.0 },
                installedPowerW = groups.sumOf { it.installedPowerW ?: 0 }
            )
        }

        return ReportModel(
            profile = ReportModel.ReportProfile.PRO,
            header = ReportModel.Header(
                projectName = "Проект №1",
                date = "22.07.2026",
                incomerLabel = "MCB_PLUS_RCD, 4P, 20A C, Icn 6000, RCD A 300mA",
                phaseMode = ReportModel.PhaseMode.THREE,
                appVersion = "3.1 (30)"
            ),
            kpis = ReportModel.Kpis(
                headlineCurrents = mapOf("A" to 13.1, "B" to 8.92, "C" to 9.73),
                totalGroups = groupsPerPhase * 3,
                installedPowerW = 7920.0,
                calculatedPowerW = 6120.0
            ),
            donut = DonutModel.PhaseDistribution(
                valuesA = mapOf(Phase.A to 13.1, Phase.B to 8.92, Phase.C to 9.73)
            ),
            phases = phases,
            professional = ProfessionalSections(
                warnings = listOf(
                    ReportWarningItem(
                        id = "w1",
                        severity = ReportWarningItem.Severity.WARNING,
                        title = "Проверьте мощность освещения",
                        message = "Для одной из линий освещения указана повышенная мощность."
                    )
                ),
                normRefs = listOf(
                    ReportNormRefItem("pue", "ПУЭ", note = "Справочно, без указания точного пункта"),
                    ReportNormRefItem("sp", "СП 256.1325800.2016", note = "Справочно, без указания точного пункта"),
                    ReportNormRefItem("iec", "ГОСТ Р 50571 / IEC 60364", note = "Справочно, без указания точного пункта")
                )
            ),
            steps = listOf(
                CalcStep(
                    name = "Установленная мощность",
                    formula = "Pуст = Σ Pустр",
                    inputs = listOf(CalcInput("deviceId=1 / base", 2200.0, "Вт")),
                    output = CalcOutput(7920.0, "Вт")
                ),
                CalcStep(
                    name = "Расчётная нагрузка",
                    formula = "Pрасч = Σ(Pустр × kспроса)",
                    inputs = listOf(
                        CalcInput("Розетка / base", 2200.0, "Вт"),
                        CalcInput("Розетка / k", 0.75),
                        CalcInput("Розетка / result", 1650.0, "Вт")
                    ),
                    output = CalcOutput(6120.0, "Вт")
                )
            ),
            assumptions = listOf(
                CalcAssumption(
                    kind = CalcAssumption.Kind.OTHER,
                    source = CoefficientSource.USER,
                    subject = "demandRatio",
                    message = "Коэффициент спроса задан для всех устройств и использован в расчёте."
                )
            )
        )
    }

    private fun sampleGroup(number: Int, phase: Phase, withRcd: Boolean): ReportGroup {
        return ReportGroup(
            title = "Группа #$number — Комната с длинным названием $number",
            number = number,
            roomName = "Комната с длинным названием $number",
            purpose = if (number % 2 == 0) "Освещение" else "Розеточная линия",
            phaseLabel = phase.name,
            installedPowerW = 1300 + number * 10,
            calculatedCurrentA = 5.4 + number / 10.0,
            switchLabel = "Автомат C16",
            rcdLabel = if (withRcd) "УЗО 30 мА" else null,
            cableLabel = "Кабель 2,5 мм²",
            devices = listOf(
                ReportDevice("Розетка бытовая", powerW = 900, currentA = 3.91),
                ReportDevice("Освещение рабочей зоны", powerW = 400, currentA = 1.74)
            )
        )
    }
}
