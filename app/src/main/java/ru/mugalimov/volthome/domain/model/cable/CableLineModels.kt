package ru.mugalimov.volthome.domain.model.cable

import ru.mugalimov.volthome.domain.model.PhaseMode

enum class ConductorMaterial(val title: String) {
    COPPER("Медь"),
    ALUMINUM("Алюминий")
}

enum class CableInsulation(val title: String, val maxOperatingTemperatureC: Int) {
    PVC_70("ПВХ, 70 °C", 70),
    XLPE_90("СПЭ, 90 °C", 90)
}

enum class CableInstallationMethod(val title: String) {
    INSULATED_WALL("В теплоизолированной стене"),
    CONDUIT_WALL("В трубе или коробе"),
    CLIPPED_DIRECT("Открыто по поверхности"),
    TRAY_FREE_AIR("На лотке / в воздухе"),
    BURIED_GROUND("В земле")
}

enum class CableCalculationSource {
    LEGACY_PRELIMINARY,
    AUTOMATIC,
    MANUAL_OVERRIDE
}

enum class CableCalculationStatus {
    PRELIMINARY,
    PASSED,
    WARNING,
    FAILED
}

enum class CableCheckStatus { PASSED, WARNING, FAILED, NOT_EVALUATED }

enum class CableCheckCode {
    LOAD_VS_BREAKER,
    BREAKER_VS_AMPACITY,
    VOLTAGE_DROP,
    PROTECTIVE_CONDUCTOR,
    SHORT_CIRCUIT,
    FAULT_LOOP
}

data class CableCheck(
    val code: CableCheckCode,
    val status: CableCheckStatus,
    val message: String
)

/** Условия, которые применяются ко всем новым линиям проекта. */
data class ProjectCableDefaults(
    val projectId: String,
    val material: ConductorMaterial = ConductorMaterial.COPPER,
    val insulation: CableInsulation = CableInsulation.PVC_70,
    val installationMethod: CableInstallationMethod = CableInstallationMethod.CONDUIT_WALL,
    val ambientTemperatureC: Int = 25,
    val groupedCircuits: Int = 1,
    val maxVoltageDropPercent: Double = 3.0
) {
    init {
        require(ambientTemperatureC in -25..70)
        require(groupedCircuits in 1..20)
        require(maxVoltageDropPercent in 0.5..10.0)
    }
}

data class CableLineInput(
    val projectId: String,
    val groupId: Long,
    val phaseMode: PhaseMode,
    val loadCurrentA: Double,
    val breakerA: Int,
    /** Длина трассы в одну сторону. Null означает предварительную оценку. */
    val lengthM: Double?,
    val powerFactor: Double = 1.0,
    val defaults: ProjectCableDefaults,
    val manualSectionMm2: Double? = null,
    val legacySectionMm2: Double? = null
)

data class CableSpec(
    val material: ConductorMaterial,
    val insulation: CableInsulation,
    val cores: Int,
    val phaseSectionMm2: Double,
    val neutralSectionMm2: Double,
    val protectiveEarthSectionMm2: Double
) {
    val compactLabel: String
        get() = "$cores×${formatSection(phaseSectionMm2)} мм²"

    private fun formatSection(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

data class CableLineCalculation(
    val projectId: String,
    val groupId: Long,
    val input: CableLineInput,
    val cable: CableSpec,
    val baseAmpacityA: Double,
    val installationFactor: Double,
    val temperatureFactor: Double,
    val groupingFactor: Double,
    val correctedAmpacityA: Double,
    val voltageDropV: Double?,
    val voltageDropPercent: Double?,
    val status: CableCalculationStatus,
    val source: CableCalculationSource,
    val checks: List<CableCheck>,
    val algorithmVersion: Int = 1,
    val datasetVersion: String = "VH-CABLE-2026.1"
) {
    val isFullyCalculated: Boolean
        get() = status != CableCalculationStatus.PRELIMINARY
}
