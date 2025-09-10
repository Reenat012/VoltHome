package ru.mugalimov.volthome.domain.model.report

data class ReportMeta(
    val date: String,
    /** Короткое текстовое описание вводного (для шапки HTML) */
    val incomerLabel: String,
    /** Токи для шапки: для 3-ф — A/B/C; для 1-ф — только A */
    val headlineCurrents: Map<String, Double>,
    /** Модель доната: распределение (3-ф) или загрузка вводного (1-ф) */
    val donut: DonutModel,
    /** Кол-во групп в проекте (для сводки) */
    val totalGroups: Int
)

data class ReportPhase(
    val name: String,                // "Фаза A"
    val groups: List<ReportGroup>
)

/**
 * Метаданные группы расширены алиасами — билдер возьмёт первое непустое.
 * Пример итоговой строки под «чипой»:
 *   Автомат C16 • Кабель ВВГнг-LS 3×2,5 мм²
 */
data class ReportGroup(
    val title: String,
    val devices: List<ReportDevice>,

    // --- Алиасы для описания коммутационного аппарата (любой из них может быть заполнен) ---
    val switchLabel: String? = null,
    val groupSwitchLabel: String? = null,
    val apparatusLabel: String? = null,
    val protectionLabel: String? = null,
    val breakerLabel: String? = null,
    val rcdLabel: String? = null,
    val deviceLabel: String? = null,

    // --- Алиасы для описания кабельной линии (любой из них может быть заполнен) ---
    val cableLabel: String? = null,
    val lineLabel: String? = null,
    val wireLabel: String? = null,
    val cableInfo: String? = null,
    val lineInfo: String? = null
)

/**
 * Числовые поля могут называться по-разному. Билдер умеет читать:
 *  - мощность: powerW / power / watt / pW   (Int/Double)
 *  - ток:      currentA / amp / a           (Double/Float/Int)
 * Если чисел нет — читаем/парсим spec (например: "8 Вт, 0.04 А" или "0,1 кВт").
 */
data class ReportDevice(
    val name: String,        // "Посудомоечная машина"
    val spec: String = "",   // "2.2 кВт, 11.8 А" (legacy)
    val powerW: Int? = null, // мощность в Вт (один из вариантов)
    val currentA: Double? = null
)