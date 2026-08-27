package ru.mugalimov.volthome

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.mugalimov.volthome.data.repository.ProjectSetup
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.data.repository.observeResolved
import ru.mugalimov.volthome.data.repository.resolve
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectObjectType

class ProjectSetupRepositoryExtensionsTest {

    @Test
    fun existingProjectModeWinsOverLegacyFallback() = runBlocking {
        val repository = FakeProjectSetupRepository(
            setup("p1", PhaseMode.SINGLE)
        )

        val resolved = repository.resolve("p1", PhaseMode.THREE)

        assertEquals(PhaseMode.SINGLE, resolved.phaseMode)
        assertEquals(PhaseMode.SINGLE, repository.observeResolved("p1", PhaseMode.THREE).first().phaseMode)
    }

    @Test
    fun legacyFallbackIsUsedOnlyToCreateMissingProjectSetup() = runBlocking {
        val repository = FakeProjectSetupRepository(null)

        assertEquals(PhaseMode.THREE, repository.resolve("p1", PhaseMode.THREE).phaseMode)
        assertEquals(PhaseMode.THREE, repository.resolve("p1", PhaseMode.SINGLE).phaseMode)
        assertEquals(1, repository.creationCount)
    }

    private class FakeProjectSetupRepository(initial: ProjectSetup?) : ProjectSetupRepository {
        private val state = MutableStateFlow(initial)
        var creationCount: Int = 0
            private set

        override suspend fun get(projectId: String): ProjectSetup? = state.value
        override fun observe(projectId: String): Flow<ProjectSetup?> = state
        override suspend fun save(setup: ProjectSetup) {
            state.value = setup
        }

        override suspend fun getOrCreate(projectId: String, fallbackPhaseMode: PhaseMode): ProjectSetup {
            state.value?.let { return it }
            return setup(projectId, fallbackPhaseMode).also {
                creationCount += 1
                state.value = it
            }
        }

        override suspend fun updatePhaseMode(projectId: String, phaseMode: PhaseMode) {
            state.value = (state.value ?: setup(projectId, phaseMode)).copy(phaseMode = phaseMode)
        }
    }

    companion object {
        private fun setup(projectId: String, phaseMode: PhaseMode) = ProjectSetup(
            projectId = projectId,
            objectType = ProjectObjectType.APARTMENT,
            phaseMode = phaseMode,
            inputPowerKw = null,
            sourceTemplateId = null,
            sourceTemplateVersion = 0,
            wizardCompleted = false
        )
    }
}
