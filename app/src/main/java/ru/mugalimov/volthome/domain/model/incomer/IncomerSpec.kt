package ru.mugalimov.volthome.domain.model.incomer

/** Параметры вводного аппарата */
data class IncomerSpec(
    val kind: IncomerKind,           // схема: Автомат+УЗО / Диф / Только автомат
    val poles: Int,                  // 2 = 1P+N, 4 = 3P+N
    val mcbRating: Int,              // номинал автомата, А
    val mcbCurve: String,            // "B" / "C" / "D"
    val icn: Int,                    // пред. откл. способность, А (например 6000)
    val rcdType: RcdType? = null,    // тип УЗО/дифа
    val rcdSensitivityMa: Int? = null,  // 30/100/300 мА
    val rcdSelectivity: RcdSelectivity = RcdSelectivity.NONE
)