package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
import kotlin.math.sqrt
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.manual.ManualDeviceDraft
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft

/**
 * Пересчёт параметров линии (номинальный ток / автомат / сечение) при изменении состава группы.
 *
 * ВАЖНО:
 * - НЕ пересобирает группы и НЕ меняет их состав/тип/фазу.
 * - Допускает повышение/понижение номиналов.
 *
 * Сейчас реализовано как "прагматичный" подбор по расчётному току.
 * Если позже захочешь 100% унификацию с GroupCalculator, можно заменить внутренности на вызов его селекторов.
 */
class RecalculateGroupLineUseCase @Inject constructor() {

    data class Params(
        val group: ManualGroupDraft,
        val devicesInGroup: List<ManualDeviceDraft>,
    )

    fun execute(p: Params): ManualGroupDraft {
        // Пустая группа — линию не держим (но группа потом должна быть удалена каскадом)
        if (p.devicesInGroup.isEmpty()) {
            return p.group.copy(
                nominalCurrent = 0.0,
                circuitBreaker = null,
                cableSection = null,
                breakerType = p.group.breakerType, // не трогаем тип, если он был
                rcdRequired = p.group.rcdRequired,
                rcdCurrent = p.group.rcdCurrent,
            )
        }

        val nominalI = calculateNominalCurrentA(p.devicesInGroup)

        // Подбор автомата:
        // - берем ближайший стандартный номинал >= расчетного
        // - можно добавить запас (например 1.1..1.25), но пока держим нейтрально: по факту.
        val breaker = selectBreakerA(nominalI)

        // Подбор сечения — грубая, но рабочая таблица (для бытовых линий).
        // Позже можно связать с ПУЭ/таблицами по материалу/способу прокладки.
        val section = selectCableSectionMm2(breaker)

        // Простейшее правило УЗО:
        // - если есть "мокрые" типы/кухня/санузел у тебя скорее кодом определяется иначе,
        // - но в draft сейчас этого нет. Поэтому НЕ навязываем rcdRequired.
        // Оставляем как было: логика УЗО будет отдельной/позже.
        return p.group.copy(
            nominalCurrent = nominalI,
            circuitBreaker = breaker,
            cableSection = section,
        )
    }

    /**
     * Расчетный ток группы по сумме устройств.
     *
     * Правила:
     * - powerW может быть null -> считаем 0
     * - demandRatio null -> 1.0
     * - powerFactor null -> 1.0
     * - AC 1ф: I = P / (U * pf) * k
     * - AC 3ф: I = P / (sqrt(3) * U_ll * pf) * k
     * - DC: считаем как 1ф для оценки (как у тебя в селекторах)
     */
    private fun calculateNominalCurrentA(devices: List<ManualDeviceDraft>): Double {
        var sum = 0.0
        for (d in devices) {
            val p = (d.powerW ?: 0).toDouble()
            if (p <= 0.0) continue

            val k = d.demandRatio ?: 1.0
            val pf = (d.powerFactor ?: 1.0).coerceAtLeast(0.1) // защита от мусора

            val i = when (d.voltageType) {
                VoltageType.AC_1PHASE -> (p / (U_1P * pf)) * k
                VoltageType.AC_3PHASE -> (p / (sqrt(3.0) * U_3P_LL * pf)) * k
                VoltageType.DC -> (p / (U_1P * pf)) * k // DC считаем как 1ф (оценка)
            }

            // Пусковые токи:
            // - у тебя это учитывается в калькуляторах для автоподбора.
            // - в manual draft пока оставляем "номинальный" ток,
            //   чтобы не прыгали автоматы при каждом перетаскивании.
            sum += i
        }
        return round2(sum)
    }

    private fun selectBreakerA(nominalI: Double): Int {
        val standards = intArrayOf(6, 10, 16, 20, 25, 32, 40, 50, 63)
        for (b in standards) {
            if (nominalI <= b.toDouble()) return b
        }
        return 63
    }

    private fun selectCableSectionMm2(breakerA: Int): Double {
        return when {
            breakerA <= 10 -> 1.5
            breakerA <= 20 -> 2.5
            breakerA <= 25 -> 4.0
            breakerA <= 32 -> 6.0
            breakerA <= 40 -> 10.0
            breakerA <= 50 -> 10.0
            else -> 16.0
        }
    }

    private fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0

    companion object {
        // Номиналы напряжений (можно вынести в общие константы, если у тебя уже есть)
        private const val U_1P = 230.0
        private const val U_3P_LL = 400.0
    }
}