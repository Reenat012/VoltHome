package ru.mugalimov.volthome.ui.format

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

object UiTextFormat {
    const val NBSP: String = "\u00A0"

    fun decimal(value: Double, digits: Int = 1): String = when (digits) {
        0 -> ExplicationNumberFormat.percent0(value)
        1 -> ExplicationNumberFormat.a(value, decimals = 1)
        else -> ExplicationNumberFormat.a(value, decimals = 2)
    }

    fun amperes(value: Double, digits: Int = 1): String =
        "${decimal(value, digits)}${NBSP}А"

    fun power(watts: Double, digits: Int = 1): String = if (watts >= 1_000.0) {
        "${decimal(watts / 1_000.0, digits)}${NBSP}кВт"
    } else {
        "${decimal(watts, 0)}${NBSP}Вт"
    }

    fun rubles(kopecks: Long): String =
        NumberFormat.getIntegerInstance(RU).format(kopecks / 100L) + NBSP + "₽"

    fun dateFromIso(value: String): String = runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }.parse(value) ?: return value
        SimpleDateFormat("d MMMM yyyy", RU).format(parsed)
    }.getOrDefault(value)

    fun plural(value: Int, one: String, few: String, many: String): String {
        val mod100 = value % 100
        val mod10 = value % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    private val RU = Locale("ru", "RU")
}
