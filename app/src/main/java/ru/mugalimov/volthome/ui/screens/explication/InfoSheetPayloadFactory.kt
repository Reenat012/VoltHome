package ru.mugalimov.volthome.ui.viewmodel.explication

import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.incomer.IncomerKind
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.incomer.RcdSelectivity
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcDetailsState
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetPayload
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetType

object InfoSheetPayloadFactory {

    enum class IncomerField { SCHEME, POLES, MCB, RCD }

    fun incomerField(
        field: IncomerField,
        incomer: IncomerSpec,
        phaseMode: PhaseMode,
        hasGroupRcds: Boolean
    ): InfoSheetPayload = when (field) {
        IncomerField.SCHEME -> scheme(incomer, phaseMode, hasGroupRcds)
        IncomerField.POLES -> poles(incomer)
        IncomerField.MCB -> mcb(incomer)
        IncomerField.RCD -> rcd(incomer, hasGroupRcds)
    }

    private fun scheme(incomer: IncomerSpec, phaseMode: PhaseMode, hasGroupRcds: Boolean): InfoSheetPayload {
        val network = if (phaseMode == PhaseMode.THREE) "3-ф" else "1-ф"
        val scheme = when (incomer.kind) {
            IncomerKind.MCB_PLUS_RCD -> "Автомат + УЗО"
            IncomerKind.RCBO -> "Дифавтомат"
            IncomerKind.MCB_ONLY -> "Только автомат"
        }

        val current = "$network • $scheme"
        val bullets = buildList {
            add("Сеть: ${if (phaseMode == PhaseMode.THREE) "3-фазная 400/230 В" else "1-фазная 230 В"}")
            add("Схема: $scheme")
            if (incomer.kind == IncomerKind.MCB_PLUS_RCD && hasGroupRcds) {
                add("Рекомендация: вводное УЗО чаще делают селективным/противопожарным при наличии групповых УЗО")
            }
        }

        return InfoSheetPayload(
            title = "Схема",
            sheetType = InfoSheetType.REFERENCE,
            currentValueText = current,
            bullets = bullets,
            calcDetailsState = CalcDetailsState.HIDDEN
        )
    }

    private fun poles(incomer: IncomerSpec): InfoSheetPayload {
        val current = when (incomer.poles) {
            4 -> "3P+N (4 пол.)"
            2 -> "1P+N (2 пол.)"
            else -> "${incomer.poles} пол."
        }

        val bullets = buildList {
            add("Полюса: $current")
            add("Смысл: отключение фазы(фаз) и нейтрали одним аппаратом")
            if (incomer.poles == 4) add("3P+N применяют для 3-ф ввода")
            if (incomer.poles == 2) add("1P+N применяют для 1-ф ввода")
        }

        return InfoSheetPayload(
            title = "Полюса",
            sheetType = InfoSheetType.REFERENCE,
            currentValueText = current,
            bullets = bullets,
            calcDetailsState = CalcDetailsState.HIDDEN
        )
    }

    private fun mcb(incomer: IncomerSpec): InfoSheetPayload {
        val icnKa = (incomer.icn / 1000)
        val polesShort = when (incomer.poles) {
            4 -> "3P+N"
            2 -> "1P+N"
            else -> "${incomer.poles}P"
        }
        val current = "${incomer.mcbCurve}${incomer.mcbRating} • ${icnKa}кА • $polesShort"

        val bullets = buildList {
            add("Номинал (In): ${incomer.mcbRating} А")
            add("Кривая: ${incomer.mcbCurve} (пусковые токи учитываются выбором B/C/D)")
            add("Отключающая способность (Icn): ${icnKa} кА")
            add("Полюса: $polesShort")
        }

        return InfoSheetPayload(
            title = "Автомат",
            sheetType = InfoSheetType.REFERENCE,
            currentValueText = current,
            bullets = bullets,
            calcDetailsState = CalcDetailsState.HIDDEN
        )
    }

    private fun rcd(incomer: IncomerSpec, hasGroupRcds: Boolean): InfoSheetPayload {
        // Если схемы без УЗО на вводе — показываем “—”
        if (incomer.kind == IncomerKind.MCB_ONLY || incomer.rcdType == null || incomer.rcdSensitivityMa == null) {
            return InfoSheetPayload(
                title = "УЗО (ввод)",
                sheetType = InfoSheetType.REFERENCE,
                currentValueText = "—",
                bullets = buildList {
                    add("На вводе УЗО не выбрано (схема: ${when (incomer.kind) {
                        IncomerKind.MCB_ONLY -> "Только автомат"
                        IncomerKind.MCB_PLUS_RCD -> "Автомат + УЗО"
                        IncomerKind.RCBO -> "Дифавтомат"
                    }})")
                    if (hasGroupRcds) add("Защиту от утечек обеспечивают групповые УЗО/дифы")
                },
                calcDetailsState = CalcDetailsState.HIDDEN
            )
        }

        val selectivityText = when (incomer.rcdSelectivity) {
            RcdSelectivity.S -> "S (селективное)"
            else -> "нет"
        }

        val current = "${incomer.rcdType} • ${incomer.rcdSensitivityMa} мА" +
                (if (incomer.rcdSelectivity == RcdSelectivity.S) " • S" else "")

        val bullets = buildList {
            add("Тип: ${incomer.rcdType}")
            add("Чувствительность: ${incomer.rcdSensitivityMa} мА")
            add("Селективность: $selectivityText")
            add("Важно: УЗО не защищает от КЗ/перегрузки — это делает автомат (или дифавтомат)")
        }

        return InfoSheetPayload(
            title = "УЗО (ввод)",
            sheetType = InfoSheetType.REFERENCE,
            currentValueText = current,
            bullets = bullets,
            calcDetailsState = CalcDetailsState.HIDDEN
        )
    }
}