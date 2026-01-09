package ru.mugalimov.volthome.domain.model

/**
 * Capabilities — это единственный источник правды:
 * что пользователь реально имеет право видеть/генерировать.
 *
 * Важно: это НЕ "оверлеи", а именно доступ к профессиональным артефактам.
 */
data class PlanCapabilities(
    /** Экспорт PDF */
    val pdfExport: Boolean,

    /** Профессиональные секции отчёта (обоснования/допущения/предупреждения/полный отчёт) */
    val professionalReportSections: Boolean,

    /** Drag & Drop фаз */
    val phaseDragAndDrop: Boolean,

    /** Расширенный редактор параметров устройства */
    val extendedDeviceEditor: Boolean,

    /** Отсутствие лимита проектов (FREE лимит снят) */
    val unlimitedProjects: Boolean,
) {
    companion object {
        fun fromPlan(plan: UserPlan): PlanCapabilities {
            val pro = plan.isPro
            return PlanCapabilities(
                pdfExport = pro,
                professionalReportSections = pro,
                phaseDragAndDrop = pro,
                extendedDeviceEditor = pro,
                unlimitedProjects = pro,
            )
        }
    }
}