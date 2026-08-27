package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.cable.CableLineCalculation
import ru.mugalimov.volthome.domain.model.cable.ProjectCableDefaults

interface CableCalculationRepository {
    suspend fun getDefaults(projectId: String): ProjectCableDefaults
    fun observeDefaults(projectId: String): Flow<ProjectCableDefaults>
    suspend fun saveDefaults(defaults: ProjectCableDefaults)
    fun observeCalculations(projectId: String): Flow<Map<Long, CableLineCalculation>>
    suspend fun getCalculations(projectId: String): Map<Long, CableLineCalculation>
    suspend fun getCalculation(groupId: Long): CableLineCalculation?
    suspend fun saveCalculation(calculation: CableLineCalculation)
    suspend fun deleteCalculation(groupId: Long)
}
