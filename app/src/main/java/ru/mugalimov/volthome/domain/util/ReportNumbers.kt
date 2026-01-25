package ru.mugalimov.volthome.domain.util

import ru.mugalimov.volthome.domain.model.Device

/**
 * ЕДИНЫЙ ИСТОЧНИК ЧИСЕЛ ДЛЯ ОТЧЁТОВ (UI / PDF).
 *
 * Назначение:
 * - гарантировать, что power / current устройства считаются ОДИНАКОВО
 *   в UI, PDF и любых отчётных представлениях
 * - убрать расхождения вида: «в UI одно, в PDF другое»
 *
 * Правила:
 * 1. Никаких fallback-расчётов в UI/PDF — только через этот util
 * 2. Всегда используем PowerCurrentNormalizer
 * 3. device() НЕ форматирует числа — только сырые значения
 */
object ReportNumbers {

    /**
     * Числовое представление устройства для отчётов.
     *
     * @param powerW   Мощность в ваттах (уже нормализованная)
     * @param currentA Ток в амперах (уже нормализованный)
     */
    data class DeviceNumbers(
        val powerW: Double?,
        val currentA: Double?
    )

    /**
     * Получить отчётные числа устройства.
     *
     * Логика:
     * - если задана мощность → используем её
     * - если задан ток → используем его
     * - если одного из значений нет → PowerCurrentNormalizer достраивает второе
     * - если оба отсутствуют → вернётся null/null
     *
     * НИКАКИХ:
     * - дефолтов «230В где-нибудь в UI»
     * - повторных вычислений в HTML / Compose
     */
    fun device(d: Device): DeviceNumbers {
        val normalized = PowerCurrentNormalizer.ensurePAndI(
            powerW = d.power?.takeIf { it > 0 },
            currentA = d.calculateCurrent().takeIf { it > 0.0 },
            voltage = d.voltage,
            powerFactor = d.powerFactor
        )

        return DeviceNumbers(
            powerW = normalized.powerW?.toDouble(),
            currentA = normalized.currentA
        )
    }
}