package ru.mugalimov.volthome.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadMode
import ru.mugalimov.volthome.ui.onboarding.hints.BaseHints
import ru.mugalimov.volthome.ui.onboarding.hints.ExplicationHints
import ru.mugalimov.volthome.ui.onboarding.hints.ManualHints
import ru.mugalimov.volthome.ui.onboarding.hints.PdfHints
import ru.mugalimov.volthome.ui.onboarding.model.ExplicationOnboardingFacts
import ru.mugalimov.volthome.ui.onboarding.model.LoadsOnboardingFacts
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId
import ru.mugalimov.volthome.ui.onboarding.model.ProjectsOnboardingFacts

class OnboardingHintsTest {

    @Test
    fun `projects hint waits until data is loaded`() {
        assertNull(BaseHints.forProjects(ProjectsOnboardingFacts(isLoading = true)))
        assertEquals(
            OnboardingHintId.PROJECTS_ADD_FIRST_PROJECT,
            BaseHints.forProjects(
                ProjectsOnboardingFacts(projectsCount = 0, isLoading = false)
            )?.hintId
        )
    }

    @Test
    fun `single phase project explains incomer load`() {
        val hint = BaseHints.forLoads(
            LoadsOnboardingFacts(
                groupsCount = 2,
                phaseMode = PhaseMode.SINGLE,
                phaseLoadMode = PhaseLoadMode.AUTO,
                isLoading = false
            )
        )

        assertEquals(OnboardingHintId.LOADS_VIEW_INPUT_LOAD, hint?.hintId)
    }

    @Test
    fun `manual onboarding starts with context before action`() {
        val facts = ExplicationOnboardingFacts(
            isLoading = false,
            isSuccess = true,
            groupsCount = 2,
            manualModeActive = true
        )

        assertEquals(
            OnboardingHintId.EXPLICATION_MANUAL_MODE_INFO,
            ManualHints.forExplicationStep(facts)?.hintId
        )
        assertEquals(
            OnboardingHintId.EXPLICATION_LONG_PRESS_DEVICE,
            ManualHints.forExplicationStep(facts.copy(manualIntroShown = true))?.hintId
        )
        assertEquals(
            OnboardingHintId.EXPLICATION_SAVE_MANUAL_CHANGES,
            ManualHints.forExplicationStep(
                facts.copy(manualIntroShown = true, longPressShown = true)
            )?.hintId
        )
    }

    @Test
    fun `overview no longer blocks pdf after it was shown`() {
        val facts = ExplicationOnboardingFacts(
            isLoading = false,
            isSuccess = true,
            groupsCount = 2,
            pdfAvailable = true,
            overviewShown = true
        )

        assertNull(ExplicationHints.forOverview(facts))
        assertEquals(OnboardingHintId.PDF_EXPORT_INFO, PdfHints.forExplication(facts)?.hintId)
        assertNull(PdfHints.forExplication(facts.copy(pdfShown = true)))
    }

    @Test
    fun `calculated shield points to dedicated panel once`() {
        val facts = ExplicationOnboardingFacts(
            isLoading = false,
            isSuccess = true,
            groupsCount = 3
        )

        assertEquals(
            OnboardingHintId.EXPLICATION_OPEN_PANEL,
            ExplicationHints.forPanel(facts)?.hintId
        )
        assertNull(ExplicationHints.forPanel(facts.copy(panelShown = true)))
    }
}
