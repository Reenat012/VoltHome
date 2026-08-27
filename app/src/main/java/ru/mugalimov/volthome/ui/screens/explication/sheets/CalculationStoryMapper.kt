package ru.mugalimov.volthome.ui.screens.explication.sheets

import ru.mugalimov.volthome.domain.model.CalcInput
import ru.mugalimov.volthome.domain.model.CalcStep
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

enum class GroupCalculationKind {
    CURRENT,
    POWER
}

/**
 * Преобразует канонические шаги расчёта в понятную пользователю историю.
 * Никакие коэффициенты здесь не вычисляются: они берутся только из входов
 * расчётного ядра с ролью `k`.
 */
object CalculationStoryMapper {

    private const val ROLE_BASE = "base"
    private const val ROLE_FACTOR = "k"
    private const val ROLE_RESULT = "result"

    fun mapGroup(
        steps: List<CalcStep>,
        kind: GroupCalculationKind,
        resultLabelOverride: String? = null,
        comparisonLabelOverride: String? = null,
        interpretationOverride: String? = null
    ): CalculationStoryUi? {
        val step = steps.singleOrNull() ?: return null
        val contributions = parseContributions(step.inputs)
        if (contributions.isEmpty()) return null

        val installedTotal = contributions.sumOf { it.baseValue }
        val resultValue = step.output.value
        val reductionPercent = if (installedTotal > 0.0) {
            ((installedTotal - resultValue) / installedTotal * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        val resultUnit = step.output.unit
        val resultLabel = resultLabelOverride ?: when (kind) {
            GroupCalculationKind.CURRENT -> "Расчётный ток группы"
            GroupCalculationKind.POWER -> "Расчётная мощность группы"
        }
        val comparisonLabel = comparisonLabelOverride ?: when (kind) {
            GroupCalculationKind.CURRENT -> "Установленный ток"
            GroupCalculationKind.POWER -> "Установленная мощность"
        }
        val impact = when {
            reductionPercent >= 0.5 ->
                "Коэффициенты спроса снизили учитываемую нагрузку на ${formatNumber(reductionPercent, 0)}%."
            else -> "Коэффициенты спроса не уменьшают нагрузку этой группы."
        }
        val interpretation = interpretationOverride ?: when (kind) {
            GroupCalculationKind.CURRENT ->
                "Это значение используется при оценке загрузки группы и предварительном выборе аппарата защиты. Установленный ток остаётся исходной характеристикой нагрузки."
            GroupCalculationKind.POWER ->
                "Это значение учитывается в общей нагрузке проекта и распределении по фазам. Установленная мощность показывает сумму паспортных мощностей устройств."
        }

        return CalculationStoryUi(
            resultLabel = resultLabel,
            resultText = formatValue(resultValue, resultUnit),
            comparisonLabel = comparisonLabel,
            comparisonText = formatValue(installedTotal, resultUnit),
            impactText = impact,
            statusText = "Исходные данные учтены",
            sourceText = "Автоматический расчёт",
            contributions = contributions.map { contribution ->
                CalculationContributionUi(
                    label = contribution.label,
                    inputText = formatValue(contribution.baseValue, contribution.baseUnit),
                    inputLabel = when (kind) {
                        GroupCalculationKind.CURRENT -> "Ток до учёта спроса"
                        GroupCalculationKind.POWER -> "Паспортная мощность"
                    },
                    inputSource = when (kind) {
                        GroupCalculationKind.CURRENT ->
                            "Рассчитано из мощности, напряжения и коэффициента мощности"
                        GroupCalculationKind.POWER -> "Параметр устройства"
                    },
                    factorText = contribution.factorValue?.let(::formatFactor),
                    factorLabel = contribution.factorValue?.let { "Коэффициент спроса" },
                    factorSource = contribution.factorValue?.let { "Параметр устройства" },
                    resultText = formatValue(contribution.resultValue, contribution.resultUnit)
                )
            },
            interpretation = interpretation,
            formulaText = localizeFormula(step.formula),
            formulaDescription = "Суммируются расчётные вклады всех устройств группы."
        )
    }

    fun mapInstalledPower(
        steps: List<CalcStep>,
        resultLabel: String = "Установленная мощность щита"
    ): CalculationStoryUi? {
        val step = steps.singleOrNull() ?: return null
        if (step.inputs.isEmpty()) return null
        val contributions = step.inputs.map { input ->
            CalculationContributionUi(
                label = input.name,
                inputText = formatValue(input.value, input.unit),
                inputLabel = "Паспортная мощность",
                inputSource = "Параметр устройства",
                resultText = formatValue(input.value, input.unit),
                resultLabel = "Вклад в сумму"
            )
        }
        return CalculationStoryUi(
            resultLabel = resultLabel,
            resultText = formatValue(step.output.value, step.output.unit),
            impactText = "Сумма паспортных мощностей: ${contributions.size} ${
                deviceWord(contributions.size)
            }.",
            statusText = "Исходные данные учтены",
            sourceText = "Автоматический расчёт",
            contributions = contributions,
            interpretation =
                "Установленная мощность показывает сумму паспортных мощностей без коэффициентов спроса. Она используется как исходная характеристика объекта.",
            formulaText = step.formula,
            formulaDescription = "Паспортные мощности всех устройств складываются без понижающих коэффициентов."
        )
    }

    internal fun formatNumber(value: Double, maxFractionDigits: Int = 2): String {
        if (!value.isFinite()) return "—"
        val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
            decimalSeparator = ','
            groupingSeparator = ' '
        }
        val pattern = if (maxFractionDigits > 0) {
            "#,##0.${"#".repeat(maxFractionDigits)}"
        } else {
            "#,##0"
        }
        return DecimalFormat(pattern, symbols).format(value)
    }

    private fun formatValue(value: Double, unit: String): String {
        if (unit == "Вт" && abs(value) >= 1000.0) {
            return "${formatNumber(value / 1000.0)} кВт"
        }
        return "${formatNumber(value)} $unit".trim()
    }

    private fun formatFactor(value: Double): String = "× ${formatNumber(value)}"

    private fun deviceWord(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> "устройств"
            mod10 == 1 -> "устройство"
            mod10 in 2..4 -> "устройства"
            else -> "устройств"
        }
    }

    private fun localizeFormula(formula: String): String = formula
        .replace("Pгр", "Pгр")
        .replace("Iгр", "Iгр")

    private data class ParsedContribution(
        val label: String,
        val baseValue: Double,
        val baseUnit: String,
        val factorValue: Double?,
        val resultValue: Double,
        val resultUnit: String
    )

    private data class MutableContribution(
        val label: String,
        var baseValue: Double? = null,
        var baseUnit: String? = null,
        var factorValue: Double? = null,
        var resultValue: Double? = null,
        var resultUnit: String? = null
    )

    private fun parseContributions(inputs: List<CalcInput>): List<ParsedContribution> {
        val parsed = linkedMapOf<String, MutableContribution>()
        inputs.forEach { input ->
            val role = parseRole(input.name) ?: return@forEach
            val item = parsed.getOrPut(role.key) { MutableContribution(role.label) }
            when (role.role) {
                ROLE_BASE -> {
                    item.baseValue = input.value
                    item.baseUnit = input.unit
                }
                ROLE_FACTOR -> item.factorValue = input.value
                ROLE_RESULT -> {
                    item.resultValue = input.value
                    item.resultUnit = input.unit
                }
            }
        }

        return parsed.values.mapNotNull { item ->
            val base = item.baseValue ?: return@mapNotNull null
            val result = item.resultValue ?: return@mapNotNull null
            ParsedContribution(
                label = item.label,
                baseValue = base,
                baseUnit = item.baseUnit.orEmpty(),
                factorValue = item.factorValue,
                resultValue = result,
                resultUnit = item.resultUnit.orEmpty()
            )
        }
    }

    private data class ParsedRole(
        val key: String,
        val label: String,
        val role: String
    )

    private fun parseRole(name: String): ParsedRole? {
        val separator = name.lastIndexOf(" / ")
        if (separator <= 0) return null
        val rawSubject = name.substring(0, separator).trim()
        val role = name.substring(separator + 3).trim()
        if (role !in setOf(ROLE_BASE, ROLE_FACTOR, ROLE_RESULT)) return null

        if (rawSubject.startsWith("deviceId=") && rawSubject.contains(";label=")) {
            val parts = rawSubject.split(";label=", limit = 2)
            val id = parts.first().removePrefix("deviceId=").trim()
            val label = parts.getOrNull(1)?.trim().orEmpty()
            if (id.isNotEmpty() && label.isNotEmpty()) {
                return ParsedRole("deviceId=$id", label, role)
            }
        }
        return ParsedRole(rawSubject, rawSubject, role)
    }
}
