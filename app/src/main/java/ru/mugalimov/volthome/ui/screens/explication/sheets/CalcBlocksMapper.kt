package ru.mugalimov.volthome.ui.screens.explication.sheets

import ru.mugalimov.volthome.domain.model.CalcStep
import java.util.Locale
import kotlin.math.abs

/**
 * Единый маппер шагов расчёта в UI-блоки (формула/подстановка/результат).
 * Коммит 3: Одна точка истины для форматирования и структуры calcBlocks.
 */
object CalcBlocksMapper {

    private const val GROUP_CURRENT_LEGACY_FORMULA = "Iгр = Σ Iрасч,i"

    fun mapSteps(steps: List<CalcStep>): List<CalcBlockUi> {
        return steps.map { step ->
            mapStep(step)
        }
    }

    private fun mapStep(step: CalcStep): CalcBlockUi {
        // --- Спец-правило для "расчётного тока группы", чтобы не было разрыва 11.76 -> 8.82 без kспроса ---
        if (step.formula.trim() == GROUP_CURRENT_LEGACY_FORMULA) {
            return mapGroupCurrentStepWithDemand(step)
        }

        // --- Default mapping ---
        return CalcBlockUi(
            formulaText = step.formula,
            substitutionLines = step.inputs.map { input ->
                "${input.name} = ${fmtNumber(input.value)} ${input.unit}"
            },
            resultText = "${fmtNumber(step.output.value)} ${step.output.unit}"
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
                formulaText = "Iгр = Σ (Iном,i × kспроса,i)",
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
                formulaText = "Iгр = Σ (Iном,i × kспроса,i)",
                substitutionLines = listOf(
                    "${input.name}: ${fmtNumber(inV)} ${input.unit}"
                ),
                resultText = "${fmtNumber(outV)} ${out.unit}"
            )
        }

        val k = outV / inV

        return CalcBlockUi(
            formulaText = "Iгр = Σ (Iном,i × kспроса,i)",
            substitutionLines = listOf(
                "${input.name}: ${fmtNumber(inV)} ${input.unit} × ${fmtNumber(k)} = ${fmtNumber(outV)} ${out.unit}"
            ),
            resultText = "${fmtNumber(outV)} ${out.unit}"
        )
    }

    internal fun fmtNumber(v: Double): String {
        val s = String.format(Locale.US, "%.2f", v)
        return s.trimEnd('0').trimEnd('.')
    }
}