package ru.mugalimov.volthome.domain.use_case

import kotlin.math.ceil
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.incomer.*

class IncomerSelector {

    data class Params(
        val groups: List<CircuitGroup>,
        val preferRcbo: Boolean = false,
        val hasGroupRcds: Boolean = true,
        // Явный тип сети (источник правды из настроек). Если null — как раньше, по группам.
        val voltageTypeOverride: VoltageType? = null
    )

    fun select(p: Params): IncomerSpec {
        val vType: VoltageType = p.voltageTypeOverride ?: inferVoltageType(p.groups)
        val is3 = vType == VoltageType.AC_3PHASE

        val perPhase = phaseCurrents(p.groups)                   // <-- из PhaseDistributor.kt
        val iBase = if (is3) {
            maxOf(
                perPhase.getOrZero(Phase.A),
                perPhase.getOrZero(Phase.B),
                perPhase.getOrZero(Phase.C)
            )
        } else {
            // В 1Ф режиме считаем строго по фазе A
            perPhase.getOrZero(Phase.A)
        }

        val iRequired = (iBase / 0.8).coerceAtLeast(6.0)        // +20% резерв
        val row = listOf(6,10,16,20,25,32,40,50,63,80,100,125,160)
        val target = ceil(iRequired).toInt()
        val mcb = row.firstOrNull { it >= target } ?: row.last()
        val poles = if (is3) 4 else 2

        val needRcdByRooms = p.groups.any { it.rcdRequired }

        val (kind, rcdType, sens, sel) = when {
            !needRcdByRooms && !p.hasGroupRcds ->
                Quad(IncomerKind.MCB_ONLY, null, null, RcdSelectivity.NONE)

            p.preferRcbo ->
                Quad(IncomerKind.RCBO, RcdType.A, if (is3) 100 else 30, RcdSelectivity.NONE)

            else ->
                Quad(
                    IncomerKind.MCB_PLUS_RCD, RcdType.A, if (is3) 300 else 100,
                    if (p.hasGroupRcds) RcdSelectivity.S else RcdSelectivity.NONE
                )
        }

        return IncomerSpec(
            kind = kind,
            poles = poles,
            mcbRating = mcb,
            mcbCurve = "C",
            icn = 6000,
            rcdType = rcdType,
            rcdSensitivityMa = sens,
            rcdSelectivity = sel
        )
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
