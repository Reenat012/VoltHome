package ru.mugalimov.volthome.domain.model

/**
 * Capabilities — это единственный источник правды:
 * что пользователь реально имеет право видеть/генерировать.
 *
 * Важно: это НЕ "оверлеи", а именно доступ к профессиональным артефактам.
 */
data class PlanCapabilities(
    /** Можно экспортировать PDF вообще */
    val canExportPdf: Boolean,

    /** Можно видеть профессиональные секции в UI: шаги/допущения/предупреждения/нормативы */
    val canSeeProfessionalEvidence: Boolean,

    /** Можно включать профессиональные секции в отчёт (HTML/PDF) */
    val canGenerateProfessionalReport: Boolean,
) {
    companion object {
        fun fromPlan(plan: UserPlan): PlanCapabilities {
            val pro = plan.isPro
            return PlanCapabilities(
                canExportPdf = pro,
                canSeeProfessionalEvidence = pro,
                canGenerateProfessionalReport = pro,
            )
        }
    }
}