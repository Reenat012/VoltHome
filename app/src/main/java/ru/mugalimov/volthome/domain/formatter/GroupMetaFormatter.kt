package ru.mugalimov.volthome.domain.formatter

import ru.mugalimov.volthome.domain.model.CircuitGroup
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Форматирует метаданные группы для отчёта.
 * Примеры:
 *  - "Автомат C16"
 *  - "Кабель 2.5 мм²"
 */
object GroupMetaFormatter {
    private val rus = DecimalFormatSymbols(Locale("ru", "RU")).apply {
        decimalSeparator = ','
        groupingSeparator = ' '
    }
    private val dfCable = DecimalFormat("#,##0.##", rus)

    fun buildSwitchLabel(g: CircuitGroup): String? {
        if (g.circuitBreaker <= 0) return null
        val type = g.breakerType.takeIf { it.isNotBlank() } ?: "C"
        return "Автомат ${type}${g.circuitBreaker}"
    }

    fun buildCableLabel(g: CircuitGroup): String? {
        if (g.cableSection <= 0) return null
        return "Кабель ${dfCable.format(g.cableSection)} мм²"
    }
}