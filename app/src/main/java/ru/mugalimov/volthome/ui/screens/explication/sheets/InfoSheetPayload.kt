package ru.mugalimov.volthome.ui.screens.explication.sheets

/**
 * Единый контракт контента для BottomSheet на экране Экспликация/Нагрузки.
 * Коммит 1: добавлена типизация sheet’ов и статус HIDDEN.
 * Коммит 2: добавлен централизованный резолвер статуса.
 */
data class InfoSheetPayload(
    val title: String,
    val sheetType: InfoSheetType = InfoSheetType.REFERENCE,
    val currentValueText: String? = null,
    val currentValueLabel: String = "Расчётное значение",
    val decisionSummary: DecisionSummaryUi? = null,
    val ruleChecks: List<RuleCheckUi> = emptyList(),
    val detailSections: List<InfoDetailSectionUi> = emptyList(),
    val bullets: List<String> = emptyList(),
    val interpretation: String? = null,
    val formulaLines: List<String> = emptyList(),
    val formulaTitle: String = "Как получено",
    val limitations: List<String> = emptyList(),
    val sourceText: String? = null,
    val normRefs: List<String> = emptyList(),
    val calcDetailsState: CalcDetailsState = CalcDetailsState.NONE,
    val calcBlocks: List<CalcBlockUi> = emptyList(),
    val calculationStory: CalculationStoryUi? = null
) {
    companion object {

        fun totalsGroups(groupsCount: Int): InfoSheetPayload {
            return InfoSheetPayload(
                title = "Группы",
                sheetType = InfoSheetType.REFERENCE,
                currentValueText = groupsCount.toString(),
                bullets = listOf(
                    "Количество групп помогает оценить заполненность щита и селективность.",
                    "При большом числе групп важно проверить структуру защиты и место в щите."
                )
            )
        }
    }
}

data class DecisionSummaryUi(
    val title: String,
    val detail: String,
    val tone: DecisionToneUi
)

enum class DecisionToneUi {
    POSITIVE,
    NEUTRAL,
    WARNING
}

data class RuleCheckUi(
    val title: String,
    val detail: String,
    val status: RuleCheckStatusUi
)

enum class RuleCheckStatusUi {
    MATCHED,
    NOT_MATCHED,
    ASSUMPTION,
    WARNING
}

data class InfoDetailSectionUi(
    val title: String,
    val lines: List<String>
)

/**
 * Единая точка истины для статуса деталей расчёта.
 *
 * Правило:
 * - REFERENCE -> HIDDEN (всегда)
 * - CALCULATION:
 *     - нет шагов -> NONE
 *     - шаги есть, но нет доступа -> LOCKED
 *     - шаги есть и доступ есть -> AVAILABLE
 */
fun resolveCalcDetailsState(
    sheetType: InfoSheetType,
    hasAccess: Boolean,
    hasSteps: Boolean
): CalcDetailsState {
    return when (sheetType) {
        InfoSheetType.REFERENCE -> CalcDetailsState.HIDDEN
        InfoSheetType.CALCULATION -> when {
            !hasSteps -> CalcDetailsState.NONE
            !hasAccess -> CalcDetailsState.LOCKED
            else -> CalcDetailsState.AVAILABLE
        }
    }
}

/**
 * Тип sheet’а:
 * - CALCULATION — расчётный, может иметь шаги
 * - REFERENCE — справочный, расчёт не применим
 */
enum class InfoSheetType {
    CALCULATION,
    REFERENCE
}

data class CalcBlockUi(
    val formulaText: String,
    val substitutionLines: List<String> = emptyList(),
    val resultText: String
)

/**
 * Семантическое представление расчёта.
 *
 * В отличие от [CalcBlockUi], модель хранит смысл отдельных частей вычисления,
 * поэтому UI может показать не технический лог, а последовательную инженерную
 * историю: исходные данные -> коэффициент -> вклад -> итог.
 */
data class CalculationStoryUi(
    val resultLabel: String,
    val resultText: String,
    val comparisonLabel: String? = null,
    val comparisonText: String? = null,
    val impactText: String? = null,
    val statusText: String = "Данные учтены",
    val sourceText: String = "Автоматический расчёт",
    val contributions: List<CalculationContributionUi> = emptyList(),
    val interpretation: String,
    val formulaText: String,
    val formulaDescription: String? = null
)

data class CalculationContributionUi(
    val label: String,
    val inputText: String,
    val inputLabel: String,
    val inputSource: String,
    val factorText: String? = null,
    val factorLabel: String? = null,
    val factorSource: String? = null,
    val resultText: String,
    val resultLabel: String = "Вклад в группу"
)
