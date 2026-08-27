package ru.mugalimov.volthome.ui.model

import ru.mugalimov.volthome.domain.model.CalcWarning
import ru.mugalimov.volthome.domain.model.incomer.IncomerAssessment
import ru.mugalimov.volthome.domain.model.incomer.IncomerAssessmentStatus
import ru.mugalimov.volthome.ui.format.UiTextFormat

enum class IncomerAssessmentTone {
    INFO,
    WARNING,
    CRITICAL
}

data class IncomerAssessmentPresentation(
    val title: String,
    val message: String,
    val tone: IncomerAssessmentTone
)

/**
 * Единый пользовательский контракт оценки ввода для экранов и PDF.
 * Завершённое состояние без замечаний намеренно не создаёт дополнительную карточку.
 */
fun IncomerAssessment.toPresentation(): IncomerAssessmentPresentation? = when (status) {
    IncomerAssessmentStatus.WITHIN_AVAILABLE_POWER -> null

    IncomerAssessmentStatus.PRELIMINARY -> IncomerAssessmentPresentation(
        title = "Мощность ввода не указана",
        message = buildString {
            append("Номинал ${spec.mcbRating} А показан предварительно по расчётной нагрузке")
            requiredMcbRatingA?.let { required ->
                if (required != spec.mcbRating) append("; требуется не менее $required А")
            }
            append(". Укажите доступную мощность проекта, чтобы проверить ограничение ввода.")
        },
        tone = IncomerAssessmentTone.INFO
    )

    IncomerAssessmentStatus.LOAD_EXCEEDS_AVAILABLE_POWER -> IncomerAssessmentPresentation(
        title = "Нагрузка превышает доступную мощность",
        message = buildString {
            append("Для нагрузки требуется ")
            append(requiredMcbRatingA?.let { "$it А" } ?: "номинал выше поддержанного диапазона")
            availablePowerKw?.let { power ->
                append(", а для ввода указано ${UiTextFormat.decimal(power, 1)} кВт")
            }
            permittedMcbRatingA?.let { permitted ->
                append(" (допустимый стандартный номинал — до $permitted А)")
            }
            append(". Уменьшите нагрузку или проверьте условия присоединения.")
        },
        tone = IncomerAssessmentTone.CRITICAL
    )

    IncomerAssessmentStatus.INCOMPLETE_PROJECT -> IncomerAssessmentPresentation(
        title = "Номинал требует проверки",
        message = if (unassignedDeviceCount > 0) {
            "В расчёт не вошло устройств: $unassignedDeviceCount. Номинал ввода нельзя считать итоговым, пока все нагрузки не распределены."
        } else {
            "Расчётные группы ещё не сформированы. Номинал ввода нельзя считать итоговым."
        },
        tone = IncomerAssessmentTone.WARNING
    )

    IncomerAssessmentStatus.REQUIRED_RATING_UNSUPPORTED -> IncomerAssessmentPresentation(
        title = "Номинал требует проверки",
        message = "Расчётная потребность ${UiTextFormat.amperes(designCurrentA)} выходит за поддержанный диапазон автоматического подбора. Требуется инженерная проверка ввода.",
        tone = IncomerAssessmentTone.CRITICAL
    )
}

fun IncomerAssessment.toCalcWarning(): CalcWarning? = toPresentation()?.let { presentation ->
    CalcWarning(
        severity = when (presentation.tone) {
            IncomerAssessmentTone.INFO -> CalcWarning.Severity.INFO
            IncomerAssessmentTone.WARNING -> CalcWarning.Severity.WARNING
            IncomerAssessmentTone.CRITICAL -> CalcWarning.Severity.CRITICAL
        },
        scope = "incomer",
        title = presentation.title,
        message = presentation.message
    )
}
