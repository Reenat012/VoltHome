package ru.mugalimov.volthome.domain.model

import ru.mugalimov.volthome.domain.config.ProjectsLimitConfig
import ru.mugalimov.volthome.domain.model.report.ReportModel.ReportProfile

/**
 * Capabilities — это единственный источник правды:
 * что пользователь реально имеет право видеть/генерировать.
 *
 * Важно: это НЕ "оверлеи", а именно доступ к профессиональным артефактам.
 */
data class PlanCapabilities(
    /**
     * PDF export actions (PRO-only):
     * сохранение / шаринг / выгрузка PDF как артефакта.
     *
     * ВАЖНО:
     * - PDF Preview (просмотр отчёта) доступен в Free и PRO.
     * - Этот флаг НЕ должен использоваться как "доступ к отчёту вообще".
     */
    val pdfExport: Boolean,

    /** Профессиональные секции отчёта (обоснования/допущения/предупреждения/полный отчёт) */
    val professionalReportSections: Boolean,

    /** Drag & Drop фаз */
    val phaseDragAndDrop: Boolean,

    /** Расширенный редактор параметров устройства */
    val extendedDeviceEditor: Boolean,

    /** Фронтальная компоновка аппаратов по DIN-рейкам */
    val panelVisualization: Boolean,

    /** Отсутствие лимита проектов (FREE лимит снят) */
    val unlimitedProjects: Boolean,

    /** Полный расчёт кабельных линий по фактическим условиям прокладки */
    val cableLineCalculation: Boolean = false,
) {

    /**
     * Контентный профиль документа (Free/Pro).
     * Не зависит от способа получения (превью/печать), зависит только от прав контента.
     */
    fun reportProfile(): ReportProfile =
        if (professionalReportSections) ReportProfile.PRO else ReportProfile.FREE

    /**
     * Лимит проектов, вытекающий из capabilities.
     *
     * Семантика:
     * - unlimitedProjects = true  => UNLIMITED
     * - unlimitedProjects = false => FREE_PROJECTS_LIMIT
     */
    fun projectsLimit(): Int =
        if (unlimitedProjects) ProjectsLimitConfig.UNLIMITED else ProjectsLimitConfig.FREE_PROJECTS_LIMIT

    companion object {
        fun fromPlan(plan: UserPlan): PlanCapabilities {
            val pro = plan.isPro
            return PlanCapabilities(
                pdfExport = pro,
                professionalReportSections = pro,
                phaseDragAndDrop = pro,
                extendedDeviceEditor = pro,
                panelVisualization = pro,
                unlimitedProjects = pro,
                cableLineCalculation = pro,
            )
        }
    }
}
