package ru.mugalimov.volthome.domain.model

/**
 * Причина показа paywall/объясняющей модалки при попытке использовать PRO-функцию.
 *
 * Важно: некоторые фичи показываются не как "Купить PRO?", а как feature-specific объяснение.
 */
enum class ProFeature {
    // existing
    PROJECTS_LIMIT,
    PHASE_DND_TEASER,
    ADVANCED_DEVICE_EDITOR,

    // professional artifacts
    PRO_REPORT,          // export actions для отчёта (save/share/export PDF) и полный PRO-отчёт
    SINGLE_LINE_DIAGRAM, // отдельный paywall для однолинейной схемы
    PANEL_VISUALIZATION, // фронтальная компоновка щита по DIN-рейкам
    CABLE_LINE_CALCULATION, // длина, условия прокладки, Iz и падение напряжения
    CALC_EXPLANATIONS,   // обоснования / шаги / прозрачность расчётов
    CALC_WARNINGS,       // предупреждения по расчёту/данным

    // ✅ Коммит 3: техподробности выбора фазы ("Подробнее" под группой)
    DECISION_DETAILS
}
