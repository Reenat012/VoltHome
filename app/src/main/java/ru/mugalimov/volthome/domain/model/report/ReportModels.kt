package ru.mugalimov.volthome.domain.model.report

data class ReportMeta(
    val date: String,                // "13.08.2025"
    val incomer: String,             // вводной аппарат в человекочитаемом виде
    val totalGroups: Int,            // всего групп
    val totalCurrent: Double,        // суммарный расчётный ток (А) — из Success.totalCurrent
    val phaseCurrents: Map<String, Double> // "A","B","C" -> ток, А
)

data class ReportPhase(
    val name: String,                // "Фаза A"
    val groups: List<ReportGroup>
)

data class ReportGroup(
    val title: String,               // "Группа #1 — Кухня"
    val devices: List<ReportDevice>
)

data class ReportDevice(
    val name: String,                // "Посудомоечная машина"
    val spec: String                 // "1.8 кВт, 8.2 А"
)