package ru.mugalimov.volthome.ui.components.device.adapter

import ru.mugalimov.volthome.core.validation.InputConstraints
import ru.mugalimov.volthome.ui.viewmodel.DeviceEditViewModel

object DeviceParamsValidator {

    const val NAME_MAX_LEN = 80
    private const val MIN_PF = 0.1
    private const val MAX_PF = 1.0
    private const val MIN_DR = 0.1
    private const val MAX_DR = 1.0

    data class ValidationResult(
        val normalized: DeviceParamsDraft,
        val errors: DeviceParamsErrors
    )

    /**
     * ЕДИНСТВЕННАЯ точка нормализации.
     * raw -> normalized + errors(на normalized)
     */
    fun validated(raw: DeviceParamsDraft): ValidationResult {
        val normalized = raw.copy(
            name = normalizeName(raw.name),
            powerText = normalizePowerText(raw.powerText),
            powerFactorText = normalizeDecimal(raw.powerFactorText),
            demandRatioText = normalizeDecimal(raw.demandRatioText)
        )
        val errors = validate(normalized)
        return ValidationResult(normalized, errors)
    }

    /**
     * validate() больше НЕ нормализует.
     * Ожидает уже нормализованный draft.
     */
    fun validate(draft: DeviceParamsDraft): DeviceParamsErrors {
        val nameErr = if (draft.name.isBlank()) "Введите имя устройства" else null

        val powerErr = validatePowerW(draft.powerText)
        val pfErr = validateRatio(draft.powerFactorText, MIN_PF, MAX_PF)
        val drErr = validateRatio(draft.demandRatioText, MIN_DR, MAX_DR)

        return DeviceParamsErrors(
            nameError = nameErr,
            powerError = powerErr,
            powerFactorError = pfErr,
            demandRatioError = drErr
        )
    }

    // ✅ утилита, чтобы VM мог валидировать без дублирования сборки Draft
    fun toDraft(ui: DeviceEditViewModel.UiState): DeviceParamsDraft =
        DeviceParamsDraft(
            name = ui.name,
            powerText = ui.powerText,
            deviceType = ui.deviceType,
            powerFactorText = ui.powerFactorText,
            demandRatioText = ui.demandRatioText,
            voltageType = ui.voltageType,
            hasMotor = ui.hasMotor,
            requiresDedicatedCircuit = ui.requiresDedicatedCircuit,
            requiresSocketConnection = ui.requiresSocketConnection
        )

    fun normalizeDecimal(text: String): String {
        val raw = text.trim().replace(',', '.')
        // убираем обычные пробелы + неразрывные + узкие неразрывные
        return raw.replace(Regex("[\\s\\u00A0\\u202F]"), "")
    }

    fun normalizeName(text: String, maxLen: Int = NAME_MAX_LEN): String {
        val normalized = text
            .trim()
            .replace(Regex("[\\s\\u00A0\\u202F]+"), " ")
        return normalized.take(maxLen)
    }

    /**
     * Нормализация мощности: отдельно, вызывается ТОЛЬКО в validated().
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

    /**
     * ВАЖНО: тут НЕТ normalizePowerText().
     * input уже должен быть нормализован.
     */
    private fun validatePowerW(input: String): String? {
        val asInt = input.toDoubleOrNull()?.toInt() ?: return "Введите число"

        if (asInt < InputConstraints.MIN_POWER_W) {
            return "Минимум ${InputConstraints.MIN_POWER_W} Вт"
        }
        if (asInt > InputConstraints.MAX_POWER_W) {
            return "Технический предел — 1 000 000 Вт"
        }
        return null
    }

    /**
     * ВАЖНО: тут НЕТ normalizeDecimal().
     * input уже должен быть нормализован.
     */
    private fun validateRatio(input: String, min: Double, max: Double): String? {
        val v = input.toDoubleOrNull() ?: return "Введите число"
        return if (v < min || v > max) "Допустимо $min — $max" else null
    }

    /**
     * Валидация с учётом плана:
     * - на free НЕ возвращаем ошибки по PF/DR
     * - на pro — возвращаем всё как есть
     */
    fun validateForPlan(draft: DeviceParamsDraft, isAllowed: Boolean): DeviceParamsErrors {
        val e = validate(draft)
        return if (isAllowed) e else e.copy(
            powerFactorError = null,
            demandRatioError = null
        )
    }
}
