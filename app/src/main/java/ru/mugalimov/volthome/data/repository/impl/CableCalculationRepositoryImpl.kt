package ru.mugalimov.volthome.data.repository.impl

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.data.local.dao.CableCalculationDao
import ru.mugalimov.volthome.data.local.entity.CableLineCalculationEntity
import ru.mugalimov.volthome.data.local.entity.ProjectCableDefaultsEntity
import ru.mugalimov.volthome.data.repository.CableCalculationRepository
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.cable.CableCalculationSource
import ru.mugalimov.volthome.domain.model.cable.CableCalculationStatus
import ru.mugalimov.volthome.domain.model.cable.CableCheck
import ru.mugalimov.volthome.domain.model.cable.CableCheckCode
import ru.mugalimov.volthome.domain.model.cable.CableCheckStatus
import ru.mugalimov.volthome.domain.model.cable.CableInsulation
import ru.mugalimov.volthome.domain.model.cable.CableInstallationMethod
import ru.mugalimov.volthome.domain.model.cable.CableLineCalculation
import ru.mugalimov.volthome.domain.model.cable.CableLineInput
import ru.mugalimov.volthome.domain.model.cable.CableSpec
import ru.mugalimov.volthome.domain.model.cable.ConductorMaterial
import ru.mugalimov.volthome.domain.model.cable.ProjectCableDefaults

@Singleton
class CableCalculationRepositoryImpl @Inject constructor(
    private val dao: CableCalculationDao
) : CableCalculationRepository {
    override suspend fun getDefaults(projectId: String): ProjectCableDefaults =
        dao.getDefaults(projectId)?.toDomain() ?: ProjectCableDefaults(projectId)

    override fun observeDefaults(projectId: String): Flow<ProjectCableDefaults> =
        dao.observeDefaults(projectId).map { it?.toDomain() ?: ProjectCableDefaults(projectId) }

    override suspend fun saveDefaults(defaults: ProjectCableDefaults) = dao.upsertDefaults(defaults.toEntity())

    override fun observeCalculations(projectId: String): Flow<Map<Long, CableLineCalculation>> =
        dao.observeCalculations(projectId).map { rows -> rows.associate { it.group_id to it.toDomain() } }

    override suspend fun getCalculations(projectId: String): Map<Long, CableLineCalculation> =
        dao.getCalculations(projectId).associate { it.group_id to it.toDomain() }

    override suspend fun getCalculation(groupId: Long): CableLineCalculation? =
        dao.getCalculation(groupId)?.toDomain()

    override suspend fun saveCalculation(calculation: CableLineCalculation) =
        dao.upsertCalculation(calculation.toEntity())

    override suspend fun deleteCalculation(groupId: Long) = dao.deleteCalculation(groupId)

    private fun ProjectCableDefaultsEntity.toDomain() = ProjectCableDefaults(
        projectId = project_id,
        material = enumOr(material, ConductorMaterial.COPPER),
        insulation = enumOr(insulation, CableInsulation.PVC_70),
        installationMethod = enumOr(installation_method, CableInstallationMethod.CONDUIT_WALL),
        ambientTemperatureC = ambient_temperature_c,
        groupedCircuits = grouped_circuits,
        maxVoltageDropPercent = max_voltage_drop_percent
    )

    private fun ProjectCableDefaults.toEntity() = ProjectCableDefaultsEntity(
        project_id = projectId,
        material = material.name,
        insulation = insulation.name,
        installation_method = installationMethod.name,
        ambient_temperature_c = ambientTemperatureC,
        grouped_circuits = groupedCircuits,
        max_voltage_drop_percent = maxVoltageDropPercent
    )

    private fun CableLineCalculationEntity.toDomain(): CableLineCalculation {
        val defaults = ProjectCableDefaults(
            projectId = project_id,
            material = enumOr(material, ConductorMaterial.COPPER),
            insulation = enumOr(insulation, CableInsulation.PVC_70),
            installationMethod = enumOr(installation_method, CableInstallationMethod.CONDUIT_WALL),
            ambientTemperatureC = ambient_temperature_c,
            groupedCircuits = grouped_circuits,
            maxVoltageDropPercent = max_voltage_drop_percent
        )
        val input = CableLineInput(
            projectId = project_id,
            groupId = group_id,
            phaseMode = enumOr(phase_mode, PhaseMode.SINGLE),
            loadCurrentA = load_current_a,
            breakerA = breaker_a,
            lengthM = length_m,
            powerFactor = power_factor,
            defaults = defaults,
            manualSectionMm2 = manual_section_mm2,
            legacySectionMm2 = phase_section_mm2
        )
        return CableLineCalculation(
            projectId = project_id,
            groupId = group_id,
            input = input,
            cable = CableSpec(defaults.material, defaults.insulation, cores, phase_section_mm2, neutral_section_mm2, pe_section_mm2),
            baseAmpacityA = base_ampacity_a,
            installationFactor = installation_factor,
            temperatureFactor = temperature_factor,
            groupingFactor = grouping_factor,
            correctedAmpacityA = corrected_ampacity_a,
            voltageDropV = voltage_drop_v,
            voltageDropPercent = voltage_drop_percent,
            status = enumOr(status, CableCalculationStatus.PRELIMINARY),
            source = enumOr(source, CableCalculationSource.LEGACY_PRELIMINARY),
            checks = decodeChecks(checks),
            algorithmVersion = algorithm_version,
            datasetVersion = dataset_version
        )
    }

    private fun CableLineCalculation.toEntity() = CableLineCalculationEntity(
        group_id = groupId,
        project_id = projectId,
        phase_mode = input.phaseMode.name,
        load_current_a = input.loadCurrentA,
        breaker_a = input.breakerA,
        length_m = input.lengthM,
        power_factor = input.powerFactor,
        material = input.defaults.material.name,
        insulation = input.defaults.insulation.name,
        installation_method = input.defaults.installationMethod.name,
        ambient_temperature_c = input.defaults.ambientTemperatureC,
        grouped_circuits = input.defaults.groupedCircuits,
        max_voltage_drop_percent = input.defaults.maxVoltageDropPercent,
        manual_section_mm2 = input.manualSectionMm2,
        phase_section_mm2 = cable.phaseSectionMm2,
        neutral_section_mm2 = cable.neutralSectionMm2,
        pe_section_mm2 = cable.protectiveEarthSectionMm2,
        cores = cable.cores,
        base_ampacity_a = baseAmpacityA,
        installation_factor = installationFactor,
        temperature_factor = temperatureFactor,
        grouping_factor = groupingFactor,
        corrected_ampacity_a = correctedAmpacityA,
        voltage_drop_v = voltageDropV,
        voltage_drop_percent = voltageDropPercent,
        status = status.name,
        source = source.name,
        checks = encodeChecks(checks),
        algorithm_version = algorithmVersion,
        dataset_version = datasetVersion,
        updated_at_epoch_ms = System.currentTimeMillis()
    )

    private fun encodeChecks(checks: List<CableCheck>): String = checks.joinToString("\n") {
        "${it.code.name}|${it.status.name}|${it.message.replace('|', '/').replace('\n', ' ')}"
    }

    private fun decodeChecks(value: String): List<CableCheck> = value.lineSequence().mapNotNull { line ->
        val parts = line.split('|', limit = 3)
        if (parts.size != 3) return@mapNotNull null
        CableCheck(enumOr(parts[0], CableCheckCode.VOLTAGE_DROP), enumOr(parts[1], CableCheckStatus.NOT_EVALUATED), parts[2])
    }.toList()

    private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
}
