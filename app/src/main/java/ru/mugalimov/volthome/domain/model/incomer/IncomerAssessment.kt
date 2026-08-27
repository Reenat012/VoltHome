package ru.mugalimov.volthome.domain.model.incomer

/**
 * Инженерная оценка ввода проекта.
 *
 * [spec] остаётся совместимым снимком аппаратов для существующих экранов и
 * генераторов. Остальные поля не дают смешивать расчётную потребность объекта
 * с фактически доступной мощностью присоединения.
 */
data class IncomerAssessment(
    val spec: IncomerSpec,
    val baseCurrentA: Double,
    val designCurrentA: Double,
    val requiredMcbRatingA: Int?,
    val availablePowerKw: Double?,
    val availableCurrentA: Double?,
    val permittedMcbRatingA: Int?,
    val status: IncomerAssessmentStatus,
    val issues: Set<IncomerIssue> = emptySet(),
    val unassignedDeviceCount: Int = 0
) {
    /** Итог нельзя выдавать за завершённый проектный подбор. */
    val isConclusive: Boolean
        get() = status == IncomerAssessmentStatus.WITHIN_AVAILABLE_POWER
}

enum class IncomerAssessmentStatus {
    /** Мощность ввода известна, расчётная потребность в её пределах. */
    WITHIN_AVAILABLE_POWER,

    /** Расчётная потребность превышает указанную мощность присоединения. */
    LOAD_EXCEEDS_AVAILABLE_POWER,

    /** В проекте есть устройства, которые не вошли в группы. */
    INCOMPLETE_PROJECT,

    /** Требуемый номинал выше диапазона, поддержанного расчётным ядром. */
    REQUIRED_RATING_UNSUPPORTED,

    /** Доступная мощность не задана: результат является предварительным. */
    PRELIMINARY
}

enum class IncomerIssue {
    AVAILABLE_POWER_UNKNOWN,
    LOAD_EXCEEDS_AVAILABLE_POWER,
    UNASSIGNED_DEVICES,
    NO_CALCULATED_GROUPS,
    REQUIRED_RATING_UNSUPPORTED,
    AVAILABLE_POWER_BELOW_SUPPORTED_RANGE
}
