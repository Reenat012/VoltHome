package ru.mugalimov.volthome.domain.model.report

import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcWarning

/**
 * Единая структурированная модель отчёта для PDF/HTML.
 *
 * Зачем:
 * - HtmlReportBuilder перестаёт зависеть от "голых чисел" и reflection.
 * - Экспорт/печать получает один объект, в котором есть всё: итоги, секции, шаги, допущения, предупреждения, ссылки на нормы.
 *
 * ВАЖНО:
 * - Сейчас ReportModel собирается в ExportPdf.kt из уже существующих meta+phases.
 * - Далее можно расширять: steps, assumptions, warnings, normRefs заполнять из расчётов.
 */
data class ReportModel(
    val header: Header,
    val kpis: Kpis,
    val donut: DonutModel,
    val phases: List<ReportPhase>,

    // Расширяемые секции
    val steps: List<CalcStep> = emptyList(),
    val assumptions: List<CalcAssumption> = emptyList(),
    val warnings: List<CalcWarning> = emptyList(),
    val normRefs: List<NormRef> = emptyList(),
) {
    data class Header(
        val projectName: String = "",
        val date: String,
        val incomerLabel: String
    )

    data class Kpis(
        val headlineCurrents: Map<String, Double>, // "A"/"B"/"C"
        val totalGroups: Int? = null,
        val totalCurrentA: Double? = null
    )

    /**
     * Шаги/объяснения расчёта (на будущее): "как получились цифры".
     */
    data class CalcStep(
        val title: String,
        val lines: List<String> = emptyList()
    )

    /**
     * Нормативные ссылки (ПУЭ/ГОСТ/СП и т.п.) — на будущее.
     */
    data class NormRef(
        val code: String,       // например "ПУЭ 7.1.34"
        val title: String = "", // "Выбор сечения..."
        val note: String = ""   // коротко "применено к ..."
    )

    companion object {
        /**
         * Мягкий адаптер из текущей пары (meta + phases) в ReportModel.
         * Это позволяет внедрить ReportModel без переписывания всего пайплайна за раз.
         */
        fun fromLegacy(
            meta: ReportMeta,
            phases: List<ReportPhase>
        ): ReportModel {
            val headline = meta.headlineCurrents

            // totalCurrentA исторически пытались вытащить reflection — убираем это.
            // Если нужно — в дальнейшем заполняй kpis.totalCurrentA из VM (где есть группы).
            val totalGroups = meta.totalGroups

            return ReportModel(
                header = Header(
                    projectName = "",
                    date = meta.date,
                    incomerLabel = meta.incomerLabel ?: ""
                ),
                kpis = Kpis(
                    headlineCurrents = headline,
                    totalGroups = totalGroups,
                    totalCurrentA = null
                ),
                donut = meta.donut,
                phases = phases
            )
        }

        /**
         * Удобный хелпер: взять фазные токи, если надо где-то собрать headlineCurrents.
         */
        fun headlineFromPhases(perPhase: Map<Phase, Double>, includeBC: Boolean): Map<String, Double> {
            val out = linkedMapOf<String, Double>()
            out["A"] = perPhase[Phase.A] ?: 0.0
            if (includeBC) {
                out["B"] = perPhase[Phase.B] ?: 0.0
                out["C"] = perPhase[Phase.C] ?: 0.0
            }
            return out
        }
    }
}