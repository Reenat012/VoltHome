package ru.mugalimov.volthome.domain.policy.protection

import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.incomer.RcdSelectivity
import ru.mugalimov.volthome.domain.model.incomer.RcdType
import ru.mugalimov.volthome.domain.model.protection.RcdKind
import ru.mugalimov.volthome.domain.model.protection.RcdSpec

object RcdSpecFactory {
    private val supportedNominalsA = listOf(16, 25, 40, 63, 80, 100, 125, 160)

    fun createGroupRecommendation(
        required: Boolean,
        leakageCurrentMa: Int?,
        breakerRatingA: Int,
        phase: Phase,
        source: CalculationSource
    ): RcdSpec? {
        if (!required) return null
        val rated = supportedNominalsA.firstOrNull { it >= breakerRatingA }
            ?: throw IllegalArgumentException(
                "Нет поддерживаемого номинала УЗО для автомата $breakerRatingA А"
            )
        return RcdSpec(
            kind = RcdKind.RCD,
            ratedCurrentA = rated,
            leakageCurrentMa = leakageCurrentMa ?: 30,
            type = RcdType.A,
            poles = if (phase == Phase.THREE_PHASE) 4 else 2,
            selectivity = RcdSelectivity.NONE,
            source = source
        )
    }
}
