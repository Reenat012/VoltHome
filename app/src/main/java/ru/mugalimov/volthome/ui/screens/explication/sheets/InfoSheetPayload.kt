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
    val bullets: List<String> = emptyList(),
    val interpretation: String? = null,
    val normRefs: List<String> = emptyList(),
    val calcDetailsState: CalcDetailsState = CalcDetailsState.NONE,
    val calcBlocks: List<CalcBlockUi> = emptyList()
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