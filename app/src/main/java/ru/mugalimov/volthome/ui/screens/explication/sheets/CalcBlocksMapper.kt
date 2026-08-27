package ru.mugalimov.volthome.ui.screens.explication.sheets

import ru.mugalimov.volthome.domain.model.CalcStep
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Единый маппер шагов расчёта в UI-блоки (формула/подстановка/результат).
 * Коммит 3: Одна точка истины для форматирования и структуры calcBlocks.
 */
object CalcBlocksMapper {

    private const val GROUP_CURRENT_LEGACY_FORMULA = "Iгр = Σ Iрасч,i"

    // Соглашение для "прозрачных" групповых шагов:
    // "<deviceName> / base" | "<deviceName> / k" | "<deviceName> / result"
    private const val ROLE_BASE = "base"
    private const val ROLE_K = "k"
    private const val ROLE_RESULT = "result"

    fun mapSteps(steps: List<CalcStep>): List<CalcBlockUi> {
        return steps.map { step ->
            mapStep(step)
        }
    }

    private fun mapStep(step: CalcStep): CalcBlockUi {
        // 1) Пробуем "прозрачный" шаблон по соглашению именования inputs.
        val transparentLines = mapTransparentSubstitutionLinesOrNull(step)
        if (transparentLines != null) {
            return CalcBlockUi(
                formulaText = step.formula,
                substitutionLines = transparentLines,
                resultText = "${fmtNumber(step.output.value)} ${step.output.unit}"
            )
        }

        // 2) Legacy-правило (историческое): только если шаг в старом формате.
        // Важно: в legacy мы НЕ должны "угадывать" k, если шаг не соответствует условиям.
        if (step.formula.trim() == GROUP_CURRENT_LEGACY_FORMULA) {
            return mapGroupCurrentStepWithDemand(step)
        }

        // 3) Default mapping (fallback)
        return CalcBlockUi(
            formulaText = step.formula,
            substitutionLines = step.inputs.map { input ->
                "${input.name} = ${fmtNumber(input.value)} ${input.unit}"
            },
            resultText = "${fmtNumber(step.output.value)} ${step.output.unit}"
        )
    }

    /**
     * Поддержка "прозрачного" шаблона из доменных inputs:
     *  - группируем inputs по deviceName и роли base|k|result
     *  - строим строку: "<device> = <base> <unit> × <k> = <result> <unit>"
     *
     * Правила:
     *  - коэффициент k не вычисляем и не угадываем
     *  - строка создаётся только при наличии полного набора base+k+result на устройство
     *
     * Возврат:
     *  - null -> шаг не похож на "прозрачный" (используем legacy/default)
     *  - list -> шаг распознан как "прозрачный"
     *      - может быть пустым (если inputs выглядят как прозрачные, но ни одного полного набора нет)
     */
    private fun mapTransparentSubstitutionLinesOrNull(step: CalcStep): List<String>? {
        val inputs = step.inputs
        if (inputs.isEmpty()) return null

        // Быстрая проверка: есть ли вообще признаки прозрачного шаблона.
        val looksLikeTransparent = inputs.any { input ->
            val parsed = parseTransparentName(input.name)
            parsed != null && (parsed.role == ROLE_BASE || parsed.role == ROLE_K || parsed.role == ROLE_RESULT)
        }
        if (!looksLikeTransparent) return null

        data class TripleParts(
            val deviceKey: String,      // уникальный ключ (deviceId=...)
            val deviceLabel: String,    // то, что показываем пользователю
            var baseValue: Double? = null,
            var baseUnit: String? = null,
            var kValue: Double? = null,
            var resultValue: Double? = null,
            var resultUnit: String? = null
        )

// Сохраняем порядок устройств как "первое появление в inputs"
        val ordered = LinkedHashMap<String, TripleParts>()

        inputs.forEach { input ->
            val parsed = parseTransparentName(input.name) ?: return@forEach
            val key = parsed.deviceKey
            val label = parsed.deviceLabel
            val role = parsed.role

            val parts = ordered.getOrPut(key) {
                TripleParts(deviceKey = key, deviceLabel = label)
            }

            when (role) {
                ROLE_BASE -> {
                    parts.baseValue = input.value
                    parts.baseUnit = input.unit
                }
                ROLE_K -> {
                    parts.kValue = input.value
                }
                ROLE_RESULT -> {
                    parts.resultValue = input.value
                    parts.resultUnit = input.unit
                }
            }
        }

        val lines = mutableListOf<String>()

        ordered.values.forEach { parts ->
            val baseV = parts.baseValue
            val baseU = parts.baseUnit
            val kV = parts.kValue
            val resV = parts.resultValue
            val resU = parts.resultUnit

            // 1) Полная триада (calculated): base × k = result
            if (baseV != null && baseU != null && kV != null && resV != null && resU != null) {
                lines += "${parts.deviceLabel} = ${fmtNumber(baseV)} $baseU × ${fmtNumber(kV)} = ${fmtNumber(resV)} $resU"
                return@forEach
            }

            // 2) Только base (installed): "<label> = <base> <unit>"
            if (baseV != null && baseU != null && kV == null && resV == null && resU == null) {
                lines += "${parts.deviceLabel} = ${fmtNumber(baseV)} $baseU"
            }
        }

        return lines
    }

    /**
     * Парсинг имени input по соглашению: "<deviceName> / <role>"
     * Возвращает Pair(deviceName, role) или null.
     */
    private data class TransparentNameParsed(
        val deviceKey: String,
        val deviceLabel: String,
        val role: String
    )

    /**
     * Поддерживаем 2 формата:
     *
     * NEW:
     *   "deviceId=<id>;label=<name> / base|k|result"
     *
     * LEGACY:
     *   "<name> / base|k|result"
     */
    private fun parseTransparentName(name: String): TransparentNameParsed? {
        val idx = name.lastIndexOf(" / ")
        if (idx <= 0) return null

        val left = name.substring(0, idx).trim()
        val role = name.substring(idx + 3).trim()
        if (left.isEmpty() || role.isEmpty()) return null

        // NEW format
        if (left.startsWith("deviceId=") && left.contains(";label=")) {
            val parts = left.split(";label=", limit = 2)
            if (parts.size == 2) {
                val idPart = parts[0].removePrefix("deviceId=").trim()
                val label = parts[1].trim()
                if (idPart.isNotEmpty() && label.isNotEmpty()) {
                    val key = "deviceId=$idPart"
                    return TransparentNameParsed(
                        deviceKey = key,
                        deviceLabel = label,
                        role = role
                    )
                }
            }
        }

        // LEGACY fallback: ключ = label
        return TransparentNameParsed(
            deviceKey = left,
            deviceLabel = left,
            role = role
        )
    }

    /**
     * Переписываем:
     *  - формула: Iгр = Σ (Iном,i × kспроса,i)
     *  - подстановка: "Розетка: 11.76 A × 0.75 = 8.82 A"
     *
     * ВАЖНО: корректно и честно делаем это только для простого кейса "один input -> один output".
     * Для множественных устройств без явных kспроса в данных мы не придумываем математику.
     */
    private fun mapGroupCurrentStepWithDemand(step: CalcStep): CalcBlockUi {
        val inputs = step.inputs
        val out = step.output

        // 1) Если не 1 input — не фантазируем, оставляем как есть (но формулу улучшим только если безопасно).
        if (inputs.size != 1) {
            return CalcBlockUi(
                formulaText = "Iгр = Σ (Iном.i × kспроса.i)",
                substitutionLines = inputs.map { input ->
                    // лучше оставить старый формат, чем придумать k
                    "${input.name} = ${fmtNumber(input.value)} ${input.unit}"
                },
                resultText = "${fmtNumber(out.value)} ${out.unit}"
            )
        }

        val input = inputs.first()
        val inV = input.value
        val outV = out.value

        // Защита от деления на 0 / мусора
        if (abs(inV) < 1e-9) {
            return CalcBlockUi(
                formulaText = "Iгр = Σ (Iном.i × kспроса.i)",
                substitutionLines = listOf(
                    "${input.name}: ${fmtNumber(inV)} ${input.unit}"
                ),
                resultText = "${fmtNumber(outV)} ${out.unit}"
            )
        }

        val k = outV / inV

        return CalcBlockUi(
            formulaText = "Iгр = Σ (Iном.i × kспроса.i)",
            substitutionLines = listOf(
                "${input.name}: ${fmtNumber(inV)} ${input.unit} × ${fmtNumber(k)} = ${fmtNumber(outV)} ${out.unit}"
            ),
            resultText = "${fmtNumber(outV)} ${out.unit}"
        )
    }

    internal fun fmtNumber(v: Double): String {
        if (!v.isFinite()) return "—"
        val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
            decimalSeparator = ','
            groupingSeparator = ' '
        }
        return DecimalFormat("#,##0.##", symbols).format(v)
    }
}
