package ru.mugalimov.volthome.domain.model.report

import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcStep
import ru.mugalimov.volthome.domain.model.CalcWarning
import ru.mugalimov.volthome.domain.model.report.professional.ProfessionalSections

data class ReportModel(
    val profile: ReportProfile,

    val header: Header,
    val kpis: Kpis,
    val donut: DonutModel,
    val phases: List<ReportPhase>,

    val professional: ProfessionalSections? = null,

    /**
     * Шаги расчёта для секции "Шаги расчёта" (PRO-only рендер).
     * Free: всегда emptyList().
     */
    val steps: List<CalcStep> = emptyList(),

    val assumptions: List<CalcAssumption> = emptyList(),

    @Deprecated(
        message = "Use professional.warnings instead",
        level = DeprecationLevel.WARNING
    )
    val warnings: List<CalcWarning> = emptyList(),

    @Deprecated(
        message = "Use professional.normRefs instead",
        level = DeprecationLevel.WARNING
    )
    val normRefs: List<NormRef> = emptyList(),
) {
    enum class ReportProfile { FREE, PRO }
    enum class PhaseMode { SINGLE, THREE }

    data class Header(
        val projectName: String = "",
        val date: String,
        val incomerLabel: String,
        val phaseMode: PhaseMode,
        val appVersion: String = ""
    )

    data class Kpis(
        val headlineCurrents: Map<String, Double>,
        val totalGroups: Int? = null,
        val totalCurrentA: Double? = null,
        val installedPowerW: Double? = null,
        val calculatedPowerW: Double? = null,
    )

    data class NormRef(
        val code: String,
        val title: String = "",
        val note: String = ""
    )

    companion object {
        fun fromLegacy(
            meta: ReportMeta,
            phases: List<ReportPhase>,
            profile: ReportProfile,
            phaseMode: PhaseMode,
            appVersion: String,
            projectName: String = ""
        ): ReportModel {
            val headline = meta.headlineCurrents
            val totalGroups = meta.totalGroups

            return ReportModel(
                profile = profile,
                header = Header(
                    projectName = projectName,
                    date = meta.date,
                    incomerLabel = meta.incomerLabel ?: "",
                    phaseMode = phaseMode,
                    appVersion = appVersion
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

        fun headlineFromPhases(perPhase: Map<Phase, Double>, includeBC: Boolean): Map<String, Double> {
            val out = linkedMapOf<String, Double>()
            out["A"] = perPhase[Phase.A] ?: 0.0
            if (includeBC) {
                out["B"] = perPhase[Phase.B] ?: 0.0
                out["C"] = perPhase[Phase.C] ?: 0.0
            }
            return out
        }

        fun sanitizeForProfile(model: ReportModel): ReportModel {
            return if (model.profile == ReportProfile.FREE) {
                model.copy(
                    steps = emptyList(),
                    assumptions = emptyList(),
                    professional = null
                )
            } else model
        }
    }
}
