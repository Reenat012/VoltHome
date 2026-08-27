package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import ru.mugalimov.volthome.domain.model.PhaseMode

/**
 * Однократная миграция legacy-проекта в проектный электрический контекст.
 * После создания строки [ProjectSetup] глобальный phaseMode больше не участвует
 * в расчёте и остаётся только резервным значением для старых проектов.
 */
suspend fun ProjectSetupRepository.resolve(
    projectId: String,
    legacyFallbackPhaseMode: PhaseMode
): ProjectSetup = getOrCreate(projectId.trim(), legacyFallbackPhaseMode)

fun ProjectSetupRepository.observeResolved(
    projectId: String,
    legacyFallbackPhaseMode: PhaseMode
): Flow<ProjectSetup> = flow {
    getOrCreate(projectId.trim(), legacyFallbackPhaseMode)
    emitAll(observe(projectId.trim()).filterNotNull())
}
