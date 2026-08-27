package ru.mugalimov.volthome.ui.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Единый формат отображаемых чисел в русскоязычном интерфейсе.
 * Десятичная запятая и неразрывный пробел перед единицей не зависят от локали устройства.
 */
object ExplicationNumberFormat {

    private val sym = DecimalFormatSymbols(Locale("ru", "RU")).apply {
        decimalSeparator = ','
        groupingSeparator = ' ' // если захочешь группировку; сейчас шаблоны без группировки
    }

    private val df1 = DecimalFormat("0.0", sym)
    private val df2 = DecimalFormat("0.00", sym)
    private val df0 = DecimalFormat("0", sym)

    fun kwFromW(watts: Int, decimals: Int = 1): String {
        val kw = watts / 1000.0
        return when (decimals) {
            0 -> df0.format(kw)
            1 -> df1.format(kw)
            else -> df2.format(kw)
        }
    }

    fun a(value: Double, decimals: Int = 1): String =
        when (decimals) {
            0 -> df0.format(value)
            1 -> df1.format(value)
            else -> df2.format(value)
        }

    fun percent0(value: Double): String = df0.format(value)

    fun kaFromA(amps: Int): String = df0.format(amps / 1000.0)
}
