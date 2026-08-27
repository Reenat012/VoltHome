package ru.mugalimov.volthome.domain.use_case

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.incomer.*

class IncomerSelector {

    data class Params(
        val groups: List<CircuitGroup>,
        val preferRcbo: Boolean = false,
        val hasGroupRcds: Boolean = true,
        /** Договорная/доступная активная мощность ввода, если пользователь её задал. */
        val availablePowerKw: Double? = null,
        /** Устройства проекта, не вошедшие ни в одну расчётную группу. */
        val unassignedDeviceCount: Int = 0,
        // Явный тип сети (источник правды из настроек). Если null — как раньше, по группам.
        val voltageTypeOverride: VoltageType? = null
    )

    /** Совместимый API. Новому коду следует использовать [assess]. */
    fun select(p: Params): IncomerSpec = assess(p).spec

    fun assess(p: Params): IncomerAssessment {
        require(p.unassignedDeviceCount >= 0) {
            "Количество нераспределённых устройств не может быть отрицательным"
        }
        p.availablePowerKw?.let {
            require(it.isFinite() && it > 0.0) {
                "Доступная мощность должна быть положительным конечным числом"
            }
        }

        val vType: VoltageType = p.voltageTypeOverride ?: inferVoltageType(p.groups)
        val is3 = vType == VoltageType.AC_3PHASE
        if (!is3) {
            val hasThreePhaseLoad = p.groups.any { group ->
                group.phase == Phase.THREE_PHASE ||
                        group.devices.any { it.voltage.type == VoltageType.AC_3PHASE }
            }
            require(!hasThreePhaseLoad) {
                "Трёхфазная нагрузка не может быть рассчитана для однофазного ввода"
            }
        }

        val vector = phaseLoadVector(p.groups)
        val iBase = if (is3) vector.max else vector.a

        // Проектное правило: расчётная нагрузка занимает не более 80% номинала.
        val designCurrent = (iBase / LOAD_UTILIZATION_LIMIT).coerceAtLeast(MIN_SUPPORTED_RATING_A.toDouble())
        val target = ceil(designCurrent).toInt()
        val requiredRating = STANDARD_RATINGS.firstOrNull { it >= target }

        val availableCurrent = p.availablePowerKw?.let { powerKw ->
            availableCurrentA(powerKw = powerKw, isThreePhase = is3)
        }
        val permittedRating = availableCurrent?.let { current ->
            STANDARD_RATINGS.lastOrNull { it <= floor(current + EPSILON).toInt() }
        }

        val issues = buildSet {
            if (p.availablePowerKw == null) add(IncomerIssue.AVAILABLE_POWER_UNKNOWN)
            if (requiredRating == null) add(IncomerIssue.REQUIRED_RATING_UNSUPPORTED)
            if (p.unassignedDeviceCount > 0) add(IncomerIssue.UNASSIGNED_DEVICES)
            if (p.groups.isEmpty()) add(IncomerIssue.NO_CALCULATED_GROUPS)
            if (availableCurrent != null && permittedRating == null) {
                add(IncomerIssue.AVAILABLE_POWER_BELOW_SUPPORTED_RANGE)
            }
            if (
                availableCurrent != null &&
                (designCurrent > availableCurrent + EPSILON ||
                    (requiredRating != null && permittedRating != null && requiredRating > permittedRating))
            ) {
                add(IncomerIssue.LOAD_EXCEEDS_AVAILABLE_POWER)
            }
        }

        // Если мощность присоединения известна, существующие экраны получают
        // допустимый по ней номинал, а не автоматически завышенный номинал по нагрузке.
        // Сам конфликт не скрывается и остаётся в assessment.status/issues.
        val mcb = when {
            permittedRating != null -> minOf(requiredRating ?: STANDARD_RATINGS.last(), permittedRating)
            requiredRating != null -> requiredRating
            else -> STANDARD_RATINGS.last()
        }
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

        val spec = IncomerSpec(
            kind = kind,
            poles = poles,
            mcbRating = mcb,
            mcbCurve = "C",
            icn = 6000,
            rcdType = rcdType,
            rcdRatedCurrentA = if (rcdType != null) mcb else null,
            rcdSensitivityMa = sens,
            rcdSelectivity = sel
        )

        val status = when {
            IncomerIssue.LOAD_EXCEEDS_AVAILABLE_POWER in issues ->
                IncomerAssessmentStatus.LOAD_EXCEEDS_AVAILABLE_POWER
            IncomerIssue.REQUIRED_RATING_UNSUPPORTED in issues ->
                IncomerAssessmentStatus.REQUIRED_RATING_UNSUPPORTED
            IncomerIssue.UNASSIGNED_DEVICES in issues ||
                IncomerIssue.NO_CALCULATED_GROUPS in issues ->
                IncomerAssessmentStatus.INCOMPLETE_PROJECT
            IncomerIssue.AVAILABLE_POWER_UNKNOWN in issues ->
                IncomerAssessmentStatus.PRELIMINARY
            else -> IncomerAssessmentStatus.WITHIN_AVAILABLE_POWER
        }

        return IncomerAssessment(
            spec = spec,
            baseCurrentA = iBase,
            designCurrentA = designCurrent,
            requiredMcbRatingA = requiredRating,
            availablePowerKw = p.availablePowerKw,
            availableCurrentA = availableCurrent,
            permittedMcbRatingA = permittedRating,
            status = status,
            issues = issues,
            unassignedDeviceCount = p.unassignedDeviceCount
        )
    }

    private fun availableCurrentA(powerKw: Double, isThreePhase: Boolean): Double =
        if (isThreePhase) {
            powerKw * 1000.0 / (sqrt(3.0) * THREE_PHASE_VOLTAGE_V)
        } else {
            powerKw * 1000.0 / SINGLE_PHASE_VOLTAGE_V
        }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private companion object {
        const val LOAD_UTILIZATION_LIMIT = 0.8
        const val SINGLE_PHASE_VOLTAGE_V = 230.0
        const val THREE_PHASE_VOLTAGE_V = 400.0
        const val MIN_SUPPORTED_RATING_A = 6
        const val EPSILON = 1e-6
        val STANDARD_RATINGS = listOf(6, 10, 16, 20, 25, 32, 40, 50, 63, 80, 100, 125, 160)
    }
}
