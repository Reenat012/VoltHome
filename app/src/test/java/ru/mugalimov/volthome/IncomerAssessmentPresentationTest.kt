package ru.mugalimov.volthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mugalimov.volthome.domain.model.incomer.IncomerAssessment
import ru.mugalimov.volthome.domain.model.incomer.IncomerAssessmentStatus
import ru.mugalimov.volthome.domain.model.incomer.IncomerKind
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.incomer.RcdSelectivity
import ru.mugalimov.volthome.ui.model.toCalcWarning
import ru.mugalimov.volthome.ui.model.toPresentation

class IncomerAssessmentPresentationTest {

    @Test
    fun preliminaryStateExplainsThatInputPowerIsMissing() {
        val presentation = assessment(IncomerAssessmentStatus.PRELIMINARY).toPresentation()!!

        assertEquals("Мощность ввода не указана", presentation.title)
        assertTrue(presentation.message.contains("предварительно"))
    }

    @Test
    fun exceededStateShowsAvailablePowerConflict() {
        val presentation = assessment(
            status = IncomerAssessmentStatus.LOAD_EXCEEDS_AVAILABLE_POWER,
            availablePowerKw = 15.0,
            requiredMcbRatingA = 40,
            permittedMcbRatingA = 25
        ).toPresentation()!!

        assertEquals("Нагрузка превышает доступную мощность", presentation.title)
        assertTrue(presentation.message.contains("40 А"))
        assertTrue(presentation.message.contains("25 А"))
    }

    @Test
    fun incompleteStateNeverPresentsRatingAsFinal() {
        val assessment = assessment(
            status = IncomerAssessmentStatus.INCOMPLETE_PROJECT,
            unassignedDeviceCount = 2
        )

        assertEquals("Номинал требует проверки", assessment.toPresentation()!!.title)
        assertEquals("incomer", assessment.toCalcWarning()!!.scope)
    }

    @Test
    fun conclusiveStateDoesNotAddNoise() {
        assertNull(assessment(IncomerAssessmentStatus.WITHIN_AVAILABLE_POWER).toPresentation())
    }

    private fun assessment(
        status: IncomerAssessmentStatus,
        availablePowerKw: Double? = null,
        requiredMcbRatingA: Int? = 25,
        permittedMcbRatingA: Int? = null,
        unassignedDeviceCount: Int = 0
    ) = IncomerAssessment(
        spec = IncomerSpec(
            kind = IncomerKind.MCB_ONLY,
            poles = 2,
            mcbRating = permittedMcbRatingA ?: requiredMcbRatingA ?: 160,
            mcbCurve = "C",
            icn = 6000,
            rcdType = null,
            rcdRatedCurrentA = null,
            rcdSensitivityMa = null,
            rcdSelectivity = RcdSelectivity.NONE
        ),
        baseCurrentA = 17.0,
        designCurrentA = 21.25,
        requiredMcbRatingA = requiredMcbRatingA,
        availablePowerKw = availablePowerKw,
        availableCurrentA = null,
        permittedMcbRatingA = permittedMcbRatingA,
        status = status,
        unassignedDeviceCount = unassignedDeviceCount
    )
}
