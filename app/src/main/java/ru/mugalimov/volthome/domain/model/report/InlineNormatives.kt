package ru.mugalimov.volthome.domain.report

/**
 * Единая точка правды для коротких inline-нормативов.
 *
 * Формат строго:
 *  - одна строка
 *  - начинается с "Норматив:"
 *  - без цитат и длинных пояснений
 *
 * Важно: пункты не придумываем. Если без точных пунктов — указываем явно.
 */
object InlineNormatives {

    enum class FactKey {
        INCOMER_SCHEME,
        MAIN_RCD,
        GROUP_RCDS,
        WET_ZONES_30MA,
        INSTALLED_POWER,
        CALCULATED_LOAD,
        NETWORK_TYPE,
        MCB,
        RCD,
    }

    /**
     * @return строка вида "Норматив: ..." или null, если для факта не задано.
     */
    fun forFact(key: FactKey): String? = when (key) {
        FactKey.NETWORK_TYPE ->
            "Норматив: ПУЭ (без точных пунктов); СП 256.1325800.2016 (без точных пунктов)"

        FactKey.GROUP_RCDS ->
            "Норматив: ПУЭ (без точных пунктов); ГОСТ Р 50571 / IEC 60364 (без точных пунктов)"

        FactKey.WET_ZONES_30MA ->
            "Норматив: ПУЭ (без точных пунктов); ГОСТ Р 50571 / IEC 60364 (без точных пунктов)"

        FactKey.MCB ->
            "Норматив: ГОСТ IEC 60898-1 / IEC 60898-1 (без точных пунктов)"

        FactKey.RCD, FactKey.MAIN_RCD ->
            "Норматив: ГОСТ Р 50571 / IEC 60364 (без точных пунктов)"

        FactKey.INSTALLED_POWER ->
            "Норматив: СП 256.1325800.2016 (без точных пунктов)"

        FactKey.CALCULATED_LOAD ->
            "Норматив: СП 256.1325800.2016 (без точных пунктов)"

        FactKey.INCOMER_SCHEME ->
            "Норматив: ПУЭ (без точных пунктов); ГОСТ Р 50571 / IEC 60364 (без точных пунктов)"
    }
}