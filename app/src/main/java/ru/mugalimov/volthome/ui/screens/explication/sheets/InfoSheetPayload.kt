package ru.mugalimov.volthome.ui.screens.explication.sheets

/**
 * Единый контракт контента для BottomSheet на экране Экспликация/Нагрузки.
 * В коммите 1 calcBlocks пока не используются (пустые).
 */
data class InfoSheetPayload(
    val title: String,
    val currentValueText: String? = null,
    val bullets: List<String> = emptyList(),
    val interpretation: String? = null,
    val normRefs: List<String> = emptyList(),
    val calcBlocks: List<CalcBlockUi> = emptyList()
) {
    companion object {

        fun totalsGroups(groupsCount: Int): InfoSheetPayload {
            return InfoSheetPayload(
                title = "Группы",
                currentValueText = groupsCount.toString(),
                bullets = listOf(
                    "Количество групп помогает оценить заполненность щита и селективность.",
                    "При большом числе групп важно проверить структуру защиты и место в щите."
                )
            )
        }

    }
}

data class CalcBlockUi(
    val formulaText: String,
    val substitutionLines: List<String> = emptyList(),
    val resultText: String
)