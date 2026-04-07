package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import java.util.Locale

/**
 * Единая техническая трассировка расчётного пайплайна.
 *
 * Задача этого файла в Коммите 1:
 * - не менять бизнес-логику,
 * - не вводить новый source of truth,
 * - а явно пометить, какой путь сейчас считаем canonical,
 *   а какой — parallel / legacy / characterization path.
 *
 * ВАЖНО:
 * - это debug/diagnostic слой;
 * - UI и доменная логика не должны принимать решения на основе этих логов;
 * - формат специально простой, чтобы grep-ом легко находить точки расчёта.
 */
object CalculationTrace {

    private const val TAG = "CALC_TRACE"

    /** Общий логгер технических этапов расчёта. */
    fun log(stage: String, message: String) {
        Log.d(TAG, "[$stage] $message")
    }

    /** Форматтер чисел для стабильного читаемого trace. */
    fun f(value: Double): String = String.format(Locale.US, "%.4f", value)
}