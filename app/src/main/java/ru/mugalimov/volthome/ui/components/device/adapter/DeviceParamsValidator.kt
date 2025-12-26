package ru.mugalimov.volthome.ui.components.device.adapter

import ru.mugalimov.volthome.core.validation.PowerValidator

object DeviceParamsValidator {

    fun validate(draft: DeviceParamsDraft): DeviceParamsErrors {
        val powerErr = validatePowerW(draft.powerText)
        val pfErr = validateRatio01(draft.powerFactorText)
        val drErr = validateRatio01(draft.demandRatioText)

        return DeviceParamsErrors(
            powerError = powerErr,
            powerFactorError = pfErr,
            demandRatioError = drErr
        )
    }

    fun normalizeDecimal(text: String): String =
        text.trim().replace(',', '.')

    fun normalizeName(text: String, maxLen: Int = 80): String =
        text.take(maxLen)

    /**
     * Делаем так же, как в DeviceEditViewModel.setPowerText():
     * - ',' -> '.'
     * - выкидываем пробелы/nbsp/narrow-nbsp
     * - оставляем только цифры и одну точку
     */
    fun normalizePowerText(text: String): String {
        val raw = text.replace(',', '.')
        val noSpaces = raw.replace(Regex("[\\s\\u00A0\\u202F]"), "")
        return buildString(noSpaces.length) {
            var dotSeen = false
            for (ch in noSpaces) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' && !dotSeen -> {
                        append(ch)
                        dotSeen = true
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun validatePowerW(input: String): String? {
        val cleaned = normalizePowerText(input)
        val asInt = cleaned.toDoubleOrNull()?.toInt()

        return when {
            cleaned.isEmpty() -> "Введите число > 0"
            asInt == null || asInt <= 0 -> "Введите число > 0"
            else -> PowerValidator.errorMessage(asInt) // ✅ единая точка правды
        }
    }

    /**
     * PF и коэффициент спроса: 0.1 — 1.0, как у тебя уже в VM.
     */
    private fun validateRatio01(input: String): String? {
        val v = normalizeDecimal(input).toDoubleOrNull()
            ?: return "Введите число"
        return when {
            v < 0.1 || v > 1.0 -> "Допустимо 0.1 — 1.0"
            else -> null
        }
    }
}