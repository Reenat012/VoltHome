package ru.mugalimov.volthome.ui.viewmodel

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.CableCalculationRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.OnboardingRepository
import ru.mugalimov.volthome.data.repository.PanelEquipmentRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository
import ru.mugalimov.volthome.data.repository.ProjectSetup
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.data.repository.observeResolved
import ru.mugalimov.volthome.data.repository.resolve
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.formatter.GroupMetaFormatter
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcWarning
import ru.mugalimov.volthome.domain.model.CalculationAlgorithm
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.cable.CableLineCalculation
import ru.mugalimov.volthome.domain.model.cable.CableInsulation
import ru.mugalimov.volthome.domain.model.cable.CableInstallationMethod
import ru.mugalimov.volthome.domain.model.cable.ConductorMaterial
import ru.mugalimov.volthome.domain.model.cable.ProjectCableDefaults
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceCalcBreakdown
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.core.analytics.PaywallSource
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.incomer.IncomerAssessment
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction
import ru.mugalimov.volthome.domain.model.manual.ManualEditSession
import ru.mugalimov.volthome.domain.model.report.DonutModel
import ru.mugalimov.volthome.domain.model.report.ReportDevice
import ru.mugalimov.volthome.domain.model.report.ReportGroup
import ru.mugalimov.volthome.domain.model.report.ReportMeta
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import ru.mugalimov.volthome.domain.model.report.professional.ProfessionalSections
import ru.mugalimov.volthome.domain.model.singleline.SingleLineDiagram
import ru.mugalimov.volthome.domain.telemetry.CreateDeviceOpBus
import ru.mugalimov.volthome.domain.use_case.BootstrapManualLockUseCase
import ru.mugalimov.volthome.domain.use_case.CalculateDeviceBreakdownUseCase
import ru.mugalimov.volthome.domain.use_case.CalculateGroupBreakdownUseCase
import ru.mugalimov.volthome.domain.use_case.CalculateShieldOverviewUseCase
import ru.mugalimov.volthome.domain.use_case.CircuitLoadCalculator
import ru.mugalimov.volthome.domain.use_case.cable.CalculateAndSaveCableLineUseCase
import ru.mugalimov.volthome.domain.use_case.GenerateSingleLineDiagramUseCase
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import ru.mugalimov.volthome.domain.use_case.SaveAutoCalculatedGroupsToLocalDbUseCase
import ru.mugalimov.volthome.domain.use_case.getOrZero
import ru.mugalimov.volthome.domain.use_case.manual.CommitManualDraftToLocalDbUseCase
import ru.mugalimov.volthome.domain.use_case.manual.ResetManualOverridesAndAutoRecalcUseCase
import ru.mugalimov.volthome.domain.use_case.phaseCurrents
import ru.mugalimov.volthome.domain.use_case.phaseLoadVector
import ru.mugalimov.volthome.domain.use_case.report.BuildProfessionalSectionsUseCase
import ru.mugalimov.volthome.domain.util.PowerCurrentNormalizer
import ru.mugalimov.volthome.ui.onboarding.model.ExplicationOnboardingFacts
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.model.toCalcWarning
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcBlocksMapper
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcDetailsState
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalculationStoryMapper
import ru.mugalimov.volthome.ui.screens.explication.sheets.GroupCalculationKind
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetPayload
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetType
import ru.mugalimov.volthome.ui.utilities.ManualDraftResetNotifier
import ru.mugalimov.volthome.ui.viewmodel.explication.InfoSheetPayloadFactory
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import ru.mugalimov.volthome.domain.use_case.EstimatePanelEquipmentCostUseCase
import ru.mugalimov.volthome.data.repository.PanelLayoutRepository
import ru.mugalimov.volthome.domain.model.pricing.ProtectionCostEstimate
import ru.mugalimov.volthome.domain.model.ProjectCoverage
import ru.mugalimov.volthome.domain.use_case.ObserveProjectCoverageUseCase
import kotlin.collections.emptyList

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ExplicationViewModel @Inject constructor(
    private val repo: ExplicationRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: PreferencesRepository,
    private val deviceRepository: DeviceRepository,
    private val cableCalculationRepository: CableCalculationRepository,
    private val calculateAndSaveCableLineUseCase: CalculateAndSaveCableLineUseCase,
    private val calculateShieldOverviewUseCase: CalculateShieldOverviewUseCase,
    private val calculateDeviceBreakdownUseCase: CalculateDeviceBreakdownUseCase,
    private val calculateGroupBreakdownUseCase: CalculateGroupBreakdownUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    val userPlanRepository: UserPlanRepository,
    private val paywallBus: PaywallBus,
    private val activeProjectDs: ActiveProjectDataStore,
    private val manualRepo: ManualEditSessionRepository,
    private val onboardingRepository: OnboardingRepository,
    private val bootstrapManualLockUseCase: BootstrapManualLockUseCase,
    private val commitManualDraftToLocalDbUseCase: CommitManualDraftToLocalDbUseCase,
    private val saveAutoCalculatedGroupsToLocalDbUseCase: SaveAutoCalculatedGroupsToLocalDbUseCase,
    private val manualDraftResetNotifier: ManualDraftResetNotifier,
    private val createDeviceOpBus: CreateDeviceOpBus,
    private val projectOwnershipRepository: ProjectOwnershipRepository,
    private val resetManualOverridesAndAutoRecalcUseCase: ResetManualOverridesAndAutoRecalcUseCase,
    private val generateSingleLineDiagramUseCase: GenerateSingleLineDiagramUseCase,
    private val estimatePanelEquipmentCostUseCase: EstimatePanelEquipmentCostUseCase,
    private val panelEquipmentRepository: PanelEquipmentRepository,
    private val panelLayoutRepository: PanelLayoutRepository,
    private val projectSetupRepository: ProjectSetupRepository,
    private val incomerSelector: IncomerSelector,
    observeProjectCoverageUseCase: ObserveProjectCoverageUseCase
) : ViewModel() {

    data class CableEditorState(
        val group: CircuitGroup,
        val lengthText: String,
        val manualSectionText: String,
        val useManualSection: Boolean,
        val defaults: ProjectCableDefaults,
        val ambientTemperatureText: String,
        val groupedCircuitsText: String,
        val maxVoltageDropText: String,
        val isSaving: Boolean = false,
        val error: String? = null
    )

    data class CableWizardLineState(
        val group: CircuitGroup,
        val lengthText: String,
        val useManualSection: Boolean,
        val manualSectionText: String
    )

    data class CableWizardState(
        val step: Int,
        val defaults: ProjectCableDefaults,
        val ambientTemperatureText: String,
        val groupedCircuitsText: String,
        val maxVoltageDropText: String,
        val lines: List<CableWizardLineState>,
        val isSaving: Boolean = false,
        val error: String? = null
    )

    val cableCalculations: StateFlow<Map<Long, CableLineCalculation>> = activeProjectDs.activeProjectId
        .filterNotNull()
        .flatMapLatest { projectId -> cableCalculationRepository.observeCalculations(projectId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val cableDefaults: StateFlow<ProjectCableDefaults?> = activeProjectDs.activeProjectId
        .filterNotNull()
        .flatMapLatest { projectId ->
            cableCalculationRepository.observeDefaults(projectId).map { it as ProjectCableDefaults? }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _cableEditor = MutableStateFlow<CableEditorState?>(null)
    val cableEditor: StateFlow<CableEditorState?> = _cableEditor.asStateFlow()

    private val _cableWizard = MutableStateFlow<CableWizardState?>(null)
    val cableWizard: StateFlow<CableWizardState?> = _cableWizard.asStateFlow()

    fun onCableOverviewClick(groups: List<CircuitGroup>) {
        if (!hasCableCalculationAccess()) {
            requestCableCalculationPaywall()
            return
        }
        openCableWizard(groups)
    }

    fun onCableLineClick(group: CircuitGroup) {
        if (!hasCableCalculationAccess()) {
            requestCableCalculationPaywall()
            return
        }
        openCableEditor(group)
    }

    fun onCableCalculationLockedClick() {
        requestCableCalculationPaywall()
    }

    fun openCableEditor(group: CircuitGroup) {
        if (!hasCableCalculationAccess()) {
            requestCableCalculationPaywall()
            return
        }
        val existing = cableCalculations.value[group.groupId]
        val projectId = currentProjectIdOrNull() ?: return
        val initialDefaults = existing?.input?.defaults ?: ProjectCableDefaults(projectId)
        _cableEditor.value = CableEditorState(
            group = group,
            lengthText = existing?.input?.lengthM?.let(::formatCableEditorNumber).orEmpty(),
            manualSectionText = existing?.input?.manualSectionMm2?.let(::formatCableEditorNumber)
                ?: formatCableEditorNumber(existing?.cable?.phaseSectionMm2 ?: group.cableSection),
            useManualSection = existing?.input?.manualSectionMm2 != null,
            defaults = initialDefaults,
            ambientTemperatureText = initialDefaults.ambientTemperatureC.toString(),
            groupedCircuitsText = initialDefaults.groupedCircuits.toString(),
            maxVoltageDropText = formatCableEditorNumber(initialDefaults.maxVoltageDropPercent)
        )
        viewModelScope.launch(ioDispatcher) {
            val persisted = cableCalculationRepository.getDefaults(projectId)
            val current = _cableEditor.value
            if (current?.group?.groupId == group.groupId && existing == null) {
                _cableEditor.value = current.copy(
                    defaults = persisted,
                    ambientTemperatureText = persisted.ambientTemperatureC.toString(),
                    groupedCircuitsText = persisted.groupedCircuits.toString(),
                    maxVoltageDropText = formatCableEditorNumber(persisted.maxVoltageDropPercent)
                )
            }
        }
    }

    fun closeCableEditor() {
        _cableEditor.value = null
    }

    fun setCableLength(value: String) {
        _cableEditor.value = _cableEditor.value?.copy(lengthText = filterCableDecimal(value), error = null)
    }

    fun setCableManualSectionEnabled(enabled: Boolean) {
        _cableEditor.value = _cableEditor.value?.copy(useManualSection = enabled, error = null)
    }

    fun setCableManualSection(value: String) {
        _cableEditor.value = _cableEditor.value?.copy(manualSectionText = filterCableDecimal(value), error = null)
    }

    fun setCableMaterial(value: ConductorMaterial) {
        _cableEditor.value = _cableEditor.value?.let {
            it.copy(defaults = it.defaults.copy(material = value), error = null)
        }
    }

    fun setCableInsulation(value: CableInsulation) {
        _cableEditor.value = _cableEditor.value?.let {
            it.copy(defaults = it.defaults.copy(insulation = value), error = null)
        }
    }

    fun setCableInstallationMethod(value: CableInstallationMethod) {
        _cableEditor.value = _cableEditor.value?.let {
            it.copy(defaults = it.defaults.copy(installationMethod = value), error = null)
        }
    }

    fun setCableAmbientTemperature(value: String) {
        _cableEditor.value = _cableEditor.value?.copy(
            ambientTemperatureText = value.filter { it.isDigit() || it == '-' }.take(3),
            error = null
        )
    }

    fun setCableGroupedCircuits(value: String) {
        _cableEditor.value = _cableEditor.value?.copy(
            groupedCircuitsText = value.filter(Char::isDigit).take(2),
            error = null
        )
    }

    fun setCableMaxVoltageDrop(value: String) {
        _cableEditor.value = _cableEditor.value?.copy(
            maxVoltageDropText = filterCableDecimal(value),
            error = null
        )
    }

    fun saveCableCalculation() {
        if (!hasCableCalculationAccess()) {
            _cableEditor.value = null
            requestCableCalculationPaywall()
            return
        }
        val editor = _cableEditor.value ?: return
        val projectId = currentProjectIdOrNull() ?: return
        val length = editor.lengthText.replace(',', '.').toDoubleOrNull()
        val manualSection = editor.manualSectionText.replace(',', '.').toDoubleOrNull()
        val ambientTemperature = editor.ambientTemperatureText.toIntOrNull()
        val groupedCircuits = editor.groupedCircuitsText.toIntOrNull()
        val maxVoltageDrop = editor.maxVoltageDropText.replace(',', '.').toDoubleOrNull()
        when {
            length == null || length <= 0.0 -> {
                _cableEditor.value = editor.copy(error = "Укажите длину линии больше 0 м")
                return
            }
            editor.useManualSection && (manualSection == null || manualSection <= 0.0) -> {
                _cableEditor.value = editor.copy(error = "Укажите сечение кабеля")
                return
            }
            ambientTemperature == null || ambientTemperature !in -25..70 -> {
                _cableEditor.value = editor.copy(error = "Температура должна быть от −25 до 70 °C")
                return
            }
            groupedCircuits == null || groupedCircuits !in 1..20 -> {
                _cableEditor.value = editor.copy(error = "Количество совместно проложенных цепей: от 1 до 20")
                return
            }
            maxVoltageDrop == null || maxVoltageDrop !in 0.5..10.0 -> {
                _cableEditor.value = editor.copy(error = "Допустимое падение напряжения: от 0,5 до 10%")
                return
            }
        }
        _cableEditor.value = editor.copy(isSaving = true, error = null)
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                cableCalculationRepository.saveDefaults(
                    editor.defaults.copy(
                        ambientTemperatureC = ambientTemperature,
                        groupedCircuits = groupedCircuits,
                        maxVoltageDropPercent = maxVoltageDrop
                    )
                )
                calculateAndSaveCableLineUseCase(
                    projectId = projectId,
                    group = editor.group,
                    phaseMode = phaseMode.value,
                    lengthM = length,
                    manualSectionMm2 = manualSection.takeIf { editor.useManualSection }
                )
            }.onSuccess {
                _cableEditor.value = null
                _events.value = UiEvent.ShowSnackbar("Кабельная линия рассчитана")
            }.onFailure { error ->
                _cableEditor.value = editor.copy(
                    isSaving = false,
                    error = error.message ?: "Не удалось рассчитать линию"
                )
            }
        }
    }

    private fun filterCableDecimal(value: String): String {
        val normalized = value.replace('.', ',')
        var separatorSeen = false
        return buildString {
            normalized.forEach { char ->
                when {
                    char.isDigit() -> append(char)
                    char == ',' && !separatorSeen -> {
                        append(char)
                        separatorSeen = true
                    }
                }
            }
        }.take(8)
    }

    private fun formatCableEditorNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().replace('.', ',')

    private fun openCableWizard(groups: List<CircuitGroup>) {
        val projectId = currentProjectIdOrNull() ?: return
        val existing = cableCalculations.value
        val initialDefaults = cableDefaults.value ?: ProjectCableDefaults(projectId)
        _cableWizard.value = CableWizardState(
            step = 1,
            defaults = initialDefaults,
            ambientTemperatureText = initialDefaults.ambientTemperatureC.toString(),
            groupedCircuitsText = initialDefaults.groupedCircuits.toString(),
            maxVoltageDropText = formatCableEditorNumber(initialDefaults.maxVoltageDropPercent),
            lines = groups
                .sortedWith(compareBy<CircuitGroup>({ (it.phase ?: Phase.A).ordinal }, { it.groupNumber }))
                .map { group ->
                    val calculation = existing[group.groupId]
                    CableWizardLineState(
                        group = group,
                        lengthText = calculation?.input?.lengthM?.let(::formatCableEditorNumber).orEmpty(),
                        useManualSection = calculation?.input?.manualSectionMm2 != null,
                        manualSectionText = calculation?.input?.manualSectionMm2?.let(::formatCableEditorNumber)
                            ?: formatCableEditorNumber(calculation?.cable?.phaseSectionMm2 ?: group.cableSection)
                    )
                }
        )
        viewModelScope.launch(ioDispatcher) {
            val persisted = cableCalculationRepository.getDefaults(projectId)
            val current = _cableWizard.value
            // Не перезаписываем выбор пользователя, если он уже успел изменить
            // параметры, пока сохранённый профиль загружался из локальной БД.
            if (
                current != null &&
                current.defaults == initialDefaults &&
                current.ambientTemperatureText == initialDefaults.ambientTemperatureC.toString() &&
                current.groupedCircuitsText == initialDefaults.groupedCircuits.toString() &&
                current.maxVoltageDropText == formatCableEditorNumber(initialDefaults.maxVoltageDropPercent)
            ) {
                _cableWizard.value = current.copy(
                    defaults = persisted,
                    ambientTemperatureText = persisted.ambientTemperatureC.toString(),
                    groupedCircuitsText = persisted.groupedCircuits.toString(),
                    maxVoltageDropText = formatCableEditorNumber(persisted.maxVoltageDropPercent)
                )
            }
        }
    }

    fun closeCableWizard() {
        _cableWizard.value = null
    }

    fun setCableWizardMaterial(value: ConductorMaterial) {
        _cableWizard.value = _cableWizard.value?.let {
            it.copy(defaults = it.defaults.copy(material = value), error = null)
        }
    }

    fun setCableWizardInsulation(value: CableInsulation) {
        _cableWizard.value = _cableWizard.value?.let {
            it.copy(defaults = it.defaults.copy(insulation = value), error = null)
        }
    }

    fun setCableWizardInstallationMethod(value: CableInstallationMethod) {
        _cableWizard.value = _cableWizard.value?.let {
            it.copy(defaults = it.defaults.copy(installationMethod = value), error = null)
        }
    }

    fun setCableWizardAmbientTemperature(value: String) {
        _cableWizard.value = _cableWizard.value?.copy(
            ambientTemperatureText = value.filter { it.isDigit() || it == '-' }.take(3),
            error = null
        )
    }

    fun setCableWizardGroupedCircuits(value: String) {
        _cableWizard.value = _cableWizard.value?.copy(
            groupedCircuitsText = value.filter(Char::isDigit).take(2),
            error = null
        )
    }

    fun setCableWizardMaxVoltageDrop(value: String) {
        _cableWizard.value = _cableWizard.value?.copy(
            maxVoltageDropText = filterCableDecimal(value),
            error = null
        )
    }

    fun setCableWizardLineLength(groupId: Long, value: String) {
        updateCableWizardLine(groupId) { it.copy(lengthText = filterCableDecimal(value)) }
    }

    fun setCableWizardManualSectionEnabled(groupId: Long, enabled: Boolean) {
        updateCableWizardLine(groupId) { it.copy(useManualSection = enabled) }
    }

    fun setCableWizardManualSection(groupId: Long, value: String) {
        updateCableWizardLine(groupId) { it.copy(manualSectionText = filterCableDecimal(value)) }
    }

    fun nextCableWizardStep() {
        val wizard = _cableWizard.value ?: return
        val parsed = parseCableWizardDefaults(wizard) ?: return
        _cableWizard.value = wizard.copy(
            step = 2,
            defaults = parsed,
            error = null
        )
    }

    fun previousCableWizardStep() {
        _cableWizard.value = _cableWizard.value?.copy(step = 1, error = null)
    }

    fun saveCableWizard() {
        if (!hasCableCalculationAccess()) {
            _cableWizard.value = null
            requestCableCalculationPaywall()
            return
        }
        val wizard = _cableWizard.value ?: return
        val projectId = currentProjectIdOrNull() ?: return
        val defaults = parseCableWizardDefaults(wizard) ?: return
        val invalidLength = wizard.lines.firstOrNull { line ->
            line.lengthText.isNotBlank() &&
                (line.lengthText.replace(',', '.').toDoubleOrNull()?.let { it <= 0.0 } != false)
        }
        if (invalidLength != null) {
            _cableWizard.value = wizard.copy(
                error = "Проверьте длину линии группы ${invalidLength.group.groupNumber}"
            )
            return
        }
        val invalidSection = wizard.lines.firstOrNull { line ->
            line.useManualSection &&
                (line.manualSectionText.replace(',', '.').toDoubleOrNull()?.let { it <= 0.0 } != false)
        }
        if (invalidSection != null) {
            _cableWizard.value = wizard.copy(
                error = "Проверьте сечение линии группы ${invalidSection.group.groupNumber}"
            )
            return
        }
        val configuredLines = wizard.lines.filter { it.lengthText.isNotBlank() }
        if (configuredLines.isEmpty()) {
            _cableWizard.value = wizard.copy(error = "Укажите длину хотя бы одной линии")
            return
        }
        _cableWizard.value = wizard.copy(isSaving = true, error = null)
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                cableCalculationRepository.saveDefaults(defaults)
                wizard.lines.forEach { line ->
                    val length = line.lengthText.replace(',', '.').toDoubleOrNull()
                    if (length == null) {
                        cableCalculationRepository.deleteCalculation(line.group.groupId)
                    } else {
                        calculateAndSaveCableLineUseCase(
                            projectId = projectId,
                            group = line.group,
                            phaseMode = phaseMode.value,
                            lengthM = length,
                            manualSectionMm2 = if (line.useManualSection) {
                                line.manualSectionText.replace(',', '.').toDoubleOrNull()
                            } else {
                                null
                            }
                        )
                    }
                }
            }.onSuccess {
                _cableWizard.value = null
                _events.value = UiEvent.ShowSnackbar("Рассчитано линий: ${configuredLines.size}")
            }.onFailure { error ->
                _cableWizard.value = wizard.copy(
                    isSaving = false,
                    error = error.message ?: "Не удалось рассчитать кабельные линии"
                )
            }
        }
    }

    private fun updateCableWizardLine(
        groupId: Long,
        transform: (CableWizardLineState) -> CableWizardLineState
    ) {
        _cableWizard.value = _cableWizard.value?.let { wizard ->
            wizard.copy(
                lines = wizard.lines.map { if (it.group.groupId == groupId) transform(it) else it },
                error = null
            )
        }
    }

    private fun parseCableWizardDefaults(wizard: CableWizardState): ProjectCableDefaults? {
        val ambientTemperature = wizard.ambientTemperatureText.toIntOrNull()
        val groupedCircuits = wizard.groupedCircuitsText.toIntOrNull()
        val maxVoltageDrop = wizard.maxVoltageDropText.replace(',', '.').toDoubleOrNull()
        val message = when {
            ambientTemperature == null || ambientTemperature !in -25..70 ->
                "Температура должна быть от −25 до 70 °C"
            groupedCircuits == null || groupedCircuits !in 1..20 ->
                "Количество совместно проложенных цепей: от 1 до 20"
            maxVoltageDrop == null || maxVoltageDrop !in 0.5..10.0 ->
                "Допустимое падение напряжения: от 0,5 до 10%"
            else -> null
        }
        if (message != null) {
            _cableWizard.value = wizard.copy(error = message)
            return null
        }
        return wizard.defaults.copy(
            ambientTemperatureC = ambientTemperature!!,
            groupedCircuits = groupedCircuits!!,
            maxVoltageDropPercent = maxVoltageDrop!!
        )
    }

    private fun hasCableCalculationAccess(): Boolean =
        userPlanRepository.planFlow.value.capabilities.cableLineCalculation

    private fun requestCableCalculationPaywall() {
        paywallBus.request(
            feature = ProFeature.CABLE_LINE_CALCULATION,
            source = PaywallSource.CABLE_CALCULATION
        )
    }

    val apparatusSelections = activeProjectDs.activeProjectId
        .filterNotNull()
        .flatMapLatest { projectId -> panelEquipmentRepository.observeSelections(projectId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val panelLayoutSnapshot = activeProjectDs.activeProjectId
        .filterNotNull()
        .flatMapLatest { projectId -> panelLayoutRepository.observe(projectId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val projectCoverage: StateFlow<ProjectCoverage> = observeProjectCoverageUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectCoverage.Empty)

    fun estimateProtectionCost(
        incomer: IncomerSpec,
        groups: List<CircuitGroup>
    ): ProtectionCostEstimate = estimatePanelEquipmentCostUseCase(
        incomer = incomer,
        groups = groups,
        selections = apparatusSelections.value,
        customModules = panelLayoutSnapshot.value?.customModules.orEmpty()
    ).unifiedProtectionView

    private val TAG_DND = "EXP_DND"
    private val TAG_MOVE = "EXP_MOVE"
    private val TAG_SESS = "EXP_SESS"

    // =========================
    // UI state
    // =========================

    private val _uiState = MutableStateFlow<GroupScreenState>(GroupScreenState.Loading)
    val uiState: StateFlow<GroupScreenState> = _uiState.asStateFlow()

    private val _isRecalculating = MutableStateFlow(false)

    // последний известный режим фаз (для buildReportSnapshotForPdf)
    private val _phaseMode = MutableStateFlow(PhaseMode.THREE)
    val phaseMode: StateFlow<PhaseMode> = _phaseMode.asStateFlow()

    private val _showResetManualConfirmDialog = MutableStateFlow(false)
    val showResetManualConfirmDialog: StateFlow<Boolean> =
        _showResetManualConfirmDialog.asStateFlow()


    // выбранный инстанс устройства для шита
    private val _selectedDevice = MutableStateFlow<Device?>(null)

    // =========================
    // InfoSheet state
    // =========================

    private val _infoSheetPayload = MutableStateFlow<InfoSheetPayload?>(null)
    val infoSheetPayload: StateFlow<InfoSheetPayload?> = _infoSheetPayload.asStateFlow()

    private val _selectedDeviceBreakdown = MutableStateFlow<DeviceCalcBreakdown?>(null)
    val selectedDeviceBreakdown: StateFlow<DeviceCalcBreakdown?> =
        _selectedDeviceBreakdown.asStateFlow()

    // ✅ Commit 1: последний opId добавления устройств (для корреляции UI visibility)
    private val _lastCreateOpId = MutableStateFlow<String?>(null)
    val lastCreateOpId: StateFlow<String?> = _lastCreateOpId.asStateFlow()

    // =========================
    // Manual session (scoped by activeProjectId)
    // =========================

    val manualSession: StateFlow<ManualEditSession?> =
        activeProjectDs.activeProjectId
            .filterNotNull()
            .flatMapLatest { projectId -> manualRepo.observeSession(projectId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // =========================
    // Manual devices cache (manual) — Коммит 3
    // =========================

    private val _manualDevicesById = MutableStateFlow<Map<Long, Device>>(emptyMap())
    val manualDevicesById: StateFlow<Map<Long, Device>> = _manualDevicesById.asStateFlow()

    private val _manualDevicesRequestVersion = MutableStateFlow(0)

    // =========================
    // Unassigned devices (manual) — Коммит 3
    // =========================

    val unassignedDevices: StateFlow<List<Device>> =
        combine(
            manualSession,
            manualDevicesById
        ) { s: ManualEditSession?, devicesById: Map<Long, Device> ->
            val draft = s?.draftState ?: return@combine emptyList()
            if (s.manualModeActive != true) return@combine emptyList()

            draft.unassignedDeviceIds
                .mapNotNull { id -> devicesById[id] }
                .sortedBy { it.name.trim().lowercase(Locale.ROOT) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Группы для отображения в MANUAL.
     *
     * ✅ Коммит 3: устройства берём ТОЛЬКО из manualDevicesById,
     * который обновляется единым batch-load по draft (groups + unassigned).
     */
    val manualDisplayGroups: StateFlow<List<CircuitGroup>> =
        combine(
            manualSession,
            manualDevicesById
        ) { s: ManualEditSession?, devicesById: Map<Long, Device> ->
            val draft = s?.draftState ?: return@combine emptyList()
            if (s.manualModeActive != true) return@combine emptyList()

            draft.groups.map { g ->
                val groupDevices = g.deviceIds.mapNotNull { id -> devicesById[id] }

                // ✅ После line policy manual draft должен уже содержать линию.
                // Если нет — логируем это как дефект draft state, а не притворяемся,
                // будто кабель всегда 2.5.
                val resolvedBreaker = g.circuitBreaker ?: run {
                    Log.e(
                        "EXP_MANUAL_GROUPS",
                        "Missing circuitBreaker in manual draft groupId=${g.groupId} groupNumber=${g.groupNumber}"
                    )
                    16
                }

                val resolvedCable = g.cableSection ?: run {
                    Log.e(
                        "EXP_MANUAL_GROUPS",
                        "Missing cableSection in manual draft groupId=${g.groupId} groupNumber=${g.groupNumber}"
                    )
                    0.0
                }

                val resolvedBreakerType = g.breakerType ?: run {
                    Log.e(
                        "EXP_MANUAL_GROUPS",
                        "Missing breakerType in manual draft groupId=${g.groupId} groupNumber=${g.groupNumber}"
                    )
                    ""
                }

                CircuitGroup(
                    groupId = g.groupId,
                    groupNumber = g.groupNumber,
                    roomName = g.roomName,
                    roomId = g.roomId,
                    groupType = g.groupType,
                    devices = groupDevices,
                    nominalCurrent = g.nominalCurrent ?: 0.0,
                    installedPowerW = CircuitLoadCalculator
                        .calculate(groupDevices)
                        .installedPowerW
                        .toInt(),
                    circuitBreaker = resolvedBreaker,
                    cableSection = resolvedCable,
                    breakerType = resolvedBreakerType,
                    whyBreakerSelected = g.whyBreakerSelected,
                    whyCableSelected = g.whyCableSelected,
                    rcdRequired = g.rcdRequired ?: false,
                    rcdCurrent = g.rcdCurrent ?: 30,
                    rcdReasonCodes = g.rcdReasons.map { it.name },
                    calculationSource = CalculationSource.MANUAL,
                    algorithmVersion = CalculationAlgorithm.VERSION,
                    phase = g.phase
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ✅ Делаем projectId доступным как StateFlow (чтобы не блокировать first() внутри collect)
    private val activeProjectIdState: StateFlow<String?> =
        activeProjectDs.activeProjectId
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val activeProjectSetup: StateFlow<ProjectSetup?> =
        activeProjectIdState
            .flatMapLatest { projectId ->
                val pid = projectId.orEmpty().trim()
                if (pid.isBlank()) {
                    flowOf(null)
                } else {
                    val legacyFallbackMode = preferencesRepository.phaseMode.first()
                    projectSetupRepository.observeResolved(pid, legacyFallbackMode)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class ExplicationElectricalContext(
        val phaseMode: PhaseMode,
        val availablePowerKw: Double?,
        val unassignedDeviceCount: Int
    )

    private val electricalContext: StateFlow<ExplicationElectricalContext> = combine(
        activeProjectSetup,
        projectCoverage
    ) { setup, coverage ->
        ExplicationElectricalContext(
            phaseMode = setup?.phaseMode ?: PhaseMode.THREE,
            availablePowerKw = setup?.inputPowerKw,
            unassignedDeviceCount = coverage.unassignedDevices.size
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ExplicationElectricalContext(PhaseMode.THREE, null, 0)
    )


    // =========================
    // DB-driven pipeline (FIX отката)
    // =========================

    // ✅ Поток групп из БД (уже project-scoped внутри репозитория)
    // ✅ Поток групп из БД (ЖЁСТКО scoped по activeProjectId на уровне VM)
    // ✅ Поток групп из БД (ЖЁСТКО scoped по activeProjectId + instant flush stale)
    private val dbGroupsFlow: StateFlow<List<CircuitGroup>> =
        activeProjectIdState
            .flatMapLatest { pidNullable ->
                val pid = pidNullable.orEmpty().trim()
                if (pid.isBlank()) {
                    flowOf(emptyList())
                } else {
                    repo.observeAllGroupByProject(pid)
                        // ✅ критично: мгновенно сбрасываем stale-группы при переключении проекта
                        .onStart { emit(emptyList()) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ✅ Последние decisions (в памяти) — пригодится для pro-секций
    private val decisionsFlow: StateFlow<List<DistributionDecision>> =
        repo.observeDistributionDecisions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ✅ Защита от параллельных запусков явного auto-recalc (user intent)
    private val autoRecalcInFlight = MutableStateFlow(false)


    data class MoveDeviceUi(
        val deviceId: Long,
        val fromGroupId: Long
    )

    private val _moveDeviceUi = MutableStateFlow<MoveDeviceUi?>(null)
    val moveDeviceUi: StateFlow<MoveDeviceUi?> = _moveDeviceUi.asStateFlow()

    companion object {
        // Виртуальный fromGroupId для устройств из "Нераспределённых"
        const val FROM_UNASSIGNED: Long = -1L
    }

    // =========================
    // Drag state (manual) — Коммит 1
    // =========================

    sealed class DragTarget {
        data class Group(val groupId: Long) : DragTarget()
        data object Unassigned : DragTarget()
        data object NewGroup : DragTarget()
    }

    data class DragState(
        val draggingDeviceId: Long? = null,
        val fromGroupId: Long? = null,

        // Стартовые координаты (в координатах root-контейнера экрана)
        val pointerStartRoot: Offset? = null,
        val itemStartRoot: Offset? = null,

        // Текущая позиция пальца (в координатах root)
        val pointerCurrentRoot: Offset? = null,

        // Текущая позиция ghost (в координатах root). Можно хранить вычисленную позицию.
        val ghostPositionRoot: Offset? = null,

        // Активная цель (hover/selected) — определяется UI на следующих коммитах
        val activeTarget: DragTarget? = null
    ) {
        /** Drag активен, если мы знаем устройство. */
        val isActive: Boolean get() = draggingDeviceId != null
    }

    private val _dragState = MutableStateFlow(DragState())
    val dragState: StateFlow<DragState> = _dragState.asStateFlow()

    // =========================
    // Events (one-shot)
    // =========================

    private val _events = MutableStateFlow<UiEvent?>(null)
    val events: StateFlow<UiEvent?> = _events.asStateFlow()

    /**
     * Флаг активного PDF/export flow.
     *
     * Нужен не для красоты, а как канонический blocking state
     * для advanced hints.
     */
    private val _isPdfExportInProgress = MutableStateFlow(false)
    val isPdfExportInProgress: StateFlow<Boolean> = _isPdfExportInProgress.asStateFlow()

    sealed class UiEvent {
        data object ExportPdfRequested : UiEvent()

        /**
         * Запрос на экспорт однолинейной схемы
         *
         * В событие передаем уже готовую доменную модель,
         * чтобы UI не ходил в DAO и не запускал расчеты
         */
        data class SingleLineDiagramRequested(
            val diagram: SingleLineDiagram
        ) : UiEvent()
        data object PdfExportActionsRequested : UiEvent()
        data object AutoAssignUnassignedRequested : UiEvent()
        data class ShowSnackbar(val message: String) : UiEvent()
    }

    fun consumeEvent() {
        _events.value = null
    }

    /**
     * Экспорт стартовал.
     *
     * Это canonical source для blocking state, а не локальный флажок в UI.
     */
    fun onPdfExportStarted() {
        _isPdfExportInProgress.value = true
    }

    /**
     * Экспорт завершился или был отменён.
     */
    fun onPdfExportFinished() {
        _isPdfExportInProgress.value = false
    }

    /**
     * Текущий activeProjectId в нормализованном виде.
     */
    private fun currentProjectIdOrNull(): String? =
        activeProjectIdState.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /**
     * Текущий activeProjectId для внешних UI-действий.
     *
     * Важно:
     * - это не manualSession.projectId;
     * - в AUTO-режиме projectId тоже должен быть доступен.
     */
    fun currentProjectId(): String? = currentProjectIdOrNull()

    /**
     * Берём manual-сессию строго по projectId.
     *
     * Это и есть правильный SoT для VM:
     * - никакого getActiveSession()
     * - никакой зависимости от "какая-то одна активная сессия"
     */
    private fun getManualSessionForProject(projectId: String): ManualEditSession? =
        manualRepo.getSession(projectId)

    /**
     * Safety-net для случая:
     * - manualActive=false
     * - manualLock=true
     * - в проекте уже нет групп в БД
     *
     * Тогда считаем lock залипшим и снимаем его, чтобы explicit auto recalc мог оживить проект.
     *
     * ВАЖНО:
     * - если manual session жива -> lock не трогаем
     * - если в БД есть группы -> lock не трогаем
     * - снимаем lock только в реально пустом проекте
     */
    private suspend fun recoverStaleManualLockIfProjectEmpty(projectId: String): Boolean {
        val pid = projectId.trim()
        if (pid.isBlank()) return false

        val session = getManualSessionForProject(pid)
        val manualActive = session?.manualModeActive == true
        if (manualActive) {
            Log.w(
                "AUTO_GATE",
                "STALE_LOCK_RECOVERY_SKIP pid=$pid reason=manualActive ver=${session?.version}"
            )
            return false
        }

        // Берём уже наблюдаемые группы текущего проекта из VM-state.
        // Для этого safety-net нам достаточно факта: пусто / не пусто.
        val groupsInDb: List<CircuitGroup> = dbGroupsFlow.value

        if (groupsInDb.isNotEmpty()) {
            Log.w(
                "AUTO_GATE",
                "STALE_LOCK_RECOVERY_SKIP pid=$pid reason=groups_exist groups=${groupsInDb.size}"
            )
            return false
        }

        val lockBefore: Boolean = runCatching { projectOwnershipRepository.isManualLock(pid) }
            .onFailure {
                Log.e("AUTO_GATE", "STALE_LOCK_RECOVERY_LOCK_READ_FAILED pid=$pid", it)
            }
            .getOrElse { false }

        if (!lockBefore) {
            Log.w(
                "AUTO_GATE",
                "STALE_LOCK_RECOVERY_SKIP pid=$pid reason=lock_already_false"
            )
            return false
        }

        return runCatching {
            projectOwnershipRepository.setManualLock(pid, false)
            val lockAfter = projectOwnershipRepository.isManualLock(pid)

            Log.w(
                "AUTO_GATE",
                "STALE_LOCK_RECOVERY_RESULT pid=$pid lockBefore=$lockBefore lockAfter=$lockAfter groups=0"
            )

            !lockAfter
        }.onFailure {
            Log.e("AUTO_GATE", "STALE_LOCK_RECOVERY_FAILED pid=$pid", it)
        }.getOrElse { false }
    }

    // =========================
    // MIXED_MANUAL warning state (сессионно)
    // =========================

    // =========================
    // MIXED_MANUAL warning state (сессионно)
    // =========================

    private val _mixedWarningBlocked = MutableStateFlow(false)

    private val _showMixedWarningDialog = MutableStateFlow(false)

    /**
     * Канонические факты для advanced onboarding на экране экспликации.
     *
     * Локальные transient-состояния вроде drag/bottom sheet/dialog
     * экран добавит сверху сам, но база готовности экрана живёт здесь.
     */
    private val manualIntroShownFlow =
        onboardingRepository.observeShown(OnboardingHintId.EXPLICATION_MANUAL_MODE_INFO)

    private val longPressShownFlow =
        onboardingRepository.observeShown(OnboardingHintId.EXPLICATION_LONG_PRESS_DEVICE)

    private val saveShownFlow =
        onboardingRepository.observeShown(OnboardingHintId.EXPLICATION_SAVE_MANUAL_CHANGES)

    private val unassignedShownFlow =
        onboardingRepository.observeShown(OnboardingHintId.EXPLICATION_UNASSIGNED_DEVICES)

    private val overviewShownFlow =
        onboardingRepository.observeShown(OnboardingHintId.EXPLICATION_OVERVIEW)

    private val pdfShownFlow =
        onboardingRepository.observeShown(OnboardingHintId.PDF_EXPORT_INFO)

    private val singleLineShownFlow =
        onboardingRepository.observeShown(OnboardingHintId.EXPLICATION_SINGLE_LINE)

    private val panelShownFlow =
        onboardingRepository.observeShown(OnboardingHintId.EXPLICATION_OPEN_PANEL)

    private data class OnboardingFactsInputs(
        val state: GroupScreenState,
        val session: ManualEditSession?,
        val unassigned: List<Device>,
        val pdfBusy: Boolean,
        val manualIntroShown: Boolean,
        val longPressShown: Boolean,
        val saveShown: Boolean,
        val unassignedShown: Boolean,
        val overviewShown: Boolean,
        val singleLineShown: Boolean,
        val panelShown: Boolean,
        val pdfShown: Boolean
    )

    val onboardingFacts: StateFlow<ExplicationOnboardingFacts> =
        combine(
            combine(
                uiState,
                manualSession,
                unassignedDevices,
                isPdfExportInProgress
            ) { state, session, unassigned, pdfBusy ->
                arrayOf(state, session, unassigned, pdfBusy)
            },
            combine(
                combine(
                    manualIntroShownFlow,
                    longPressShownFlow,
                    saveShownFlow,
                    unassignedShownFlow
                ) { manualIntroShown, longPressShown, saveShown, unassignedShown ->
                    arrayOf(manualIntroShown, longPressShown, saveShown, unassignedShown)
                },
                combine(
                    overviewShownFlow,
                    singleLineShownFlow,
                    panelShownFlow
                ) { overview, singleLine, panel ->
                    arrayOf(overview, singleLine, panel)
                },
                pdfShownFlow
            ) { progress, resultHints, pdfShown ->
                arrayOf(
                    progress[0],
                    progress[1],
                    progress[2],
                    progress[3],
                    resultHints[0],
                    resultHints[1],
                    resultHints[2],
                    pdfShown
                )
            }
        ) { left, right ->
            OnboardingFactsInputs(
                state = left[0] as GroupScreenState,
                session = left[1] as ManualEditSession?,
                unassigned = left[2] as List<Device>,
                pdfBusy = left[3] as Boolean,
                manualIntroShown = right[0] as Boolean,
                longPressShown = right[1] as Boolean,
                saveShown = right[2] as Boolean,
                unassignedShown = right[3] as Boolean,
                overviewShown = right[4] as Boolean,
                singleLineShown = right[5] as Boolean,
                panelShown = right[6] as Boolean,
                pdfShown = right[7] as Boolean
            )
        }.map { input ->
            val success = input.state as? GroupScreenState.Success

            ExplicationOnboardingFacts(
                isLoading = input.state is GroupScreenState.Loading,
                isSuccess = success != null,
                groupsCount = success?.groups?.size ?: 0,
                manualModeActive = input.session?.manualModeActive == true,
                unassignedCount = input.unassigned.size,
                pdfAvailable = success != null,
                manualIntroShown = input.manualIntroShown,
                longPressShown = input.longPressShown,
                saveShown = input.saveShown,
                unassignedShown = input.unassignedShown,
                overviewShown = input.overviewShown,
                singleLineShown = input.singleLineShown,
                panelShown = input.panelShown,
                pdfShown = input.pdfShown,
                pdfExportFlowActive = input.pdfBusy
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ExplicationOnboardingFacts()
        )

    private data class PendingMixedMove(
        val deviceId: Long,
        val fromGroupId: Long,
        val targetGroupId: Long
    )

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)


    // =========================
    // Init
    // =========================

    init {
        // Настройки активного проекта являются источником правды. Глобальная
        // preference остаётся только fallback для старых проектов без ProjectSetup.
        viewModelScope.launch(ioDispatcher) {
            electricalContext.collect { context ->
                _phaseMode.value = context.phaseMode
            }
        }

        // Полный кабельный расчёт хранит снимок токов, автомата, фазы и условий.
        // После AUTO-перестройки или сохранения ручного draft эти значения могут
        // измениться. Пересчитываем только уже настроенные пользователем линии:
        // линии без введённой длины здесь не создаём и не выдаём за полные.
        viewModelScope.launch(ioDispatcher) {
            data class CableSyncSnapshot(
                val projectId: String,
                val groups: List<CircuitGroup>,
                val calculations: Map<Long, CableLineCalculation>,
                val projectPhaseMode: PhaseMode
            )

            combine(
                activeProjectIdState,
                dbGroupsFlow,
                cableCalculations,
                phaseMode
            ) { projectId, groups, calculations, projectPhaseMode ->
                CableSyncSnapshot(
                    projectId = projectId.orEmpty().trim(),
                    groups = groups,
                    calculations = calculations,
                    projectPhaseMode = projectPhaseMode
                )
            }.collect { snapshot ->
                if (snapshot.projectId.isBlank() || snapshot.calculations.isEmpty()) {
                    return@collect
                }
                val defaults = cableCalculationRepository.getDefaults(snapshot.projectId)
                val groupsById = snapshot.groups.associateBy(CircuitGroup::groupId)

                snapshot.calculations.values.forEach { existing ->
                    val length = existing.input.lengthM?.takeIf { it > 0.0 }
                        ?: return@forEach
                    val group = groupsById[existing.groupId] ?: return@forEach
                    val expectedLineMode = if (
                        snapshot.projectPhaseMode == PhaseMode.THREE &&
                        group.phase == Phase.THREE_PHASE
                    ) {
                        PhaseMode.THREE
                    } else {
                        PhaseMode.SINGLE
                    }
                    val expectedPowerFactor = (group.devices.minOfOrNull { it.powerFactor } ?: 1.0)
                        .coerceIn(0.1, 1.0)
                    val isStale = existing.input.phaseMode != expectedLineMode ||
                        existing.input.loadCurrentA != group.nominalCurrent ||
                        existing.input.breakerA != group.circuitBreaker ||
                        existing.input.powerFactor != expectedPowerFactor ||
                        existing.input.defaults != defaults

                    if (isStale) {
                        calculateAndSaveCableLineUseCase(
                            projectId = snapshot.projectId,
                            group = group,
                            phaseMode = snapshot.projectPhaseMode,
                            lengthM = length,
                            powerFactor = expectedPowerFactor,
                            manualSectionMm2 = existing.input.manualSectionMm2
                        )
                    }
                }
            }
        }

        // Временное безопасное правило:
        // automatic bootstrap manual-lock на старте отключён.
        // Пока нет железного stale-detector, persisted manualLock сохраняем как есть.

        // 2) Kill-process UX:
        // Если manual ожидался (маркер стоит), но сессии нет => процесс был убит => показываем уведомление.
        viewModelScope.launch(ioDispatcher) {
            activeProjectDs.activeProjectId
                .filterNotNull()
                .distinctUntilChanged()
                .collect { projectId ->
                    val session = getManualSessionForProject(projectId)
                    val hasSession = session?.manualModeActive == true

                    if (manualDraftResetNotifier.consumeResetIfNeeded(
                            projectId,
                            hasActiveSession = hasSession
                        )
                    ) {
                        _events.value = UiEvent.ShowSnackbar("Черновик ручного режима был сброшен")
                    }
                }
        }

        viewModelScope.launch(ioDispatcher) {
            manualSession.collect { s ->
                if (s == null) {
                    Log.d(TAG_SESS, "manualSession=null")
                } else {
                    Log.d(
                        TAG_SESS,
                        "manualSession pid=${s.projectId} active=${s.manualModeActive} ver=${s.version} " +
                                "groups=${s.draftState.groups.size} unassigned=${s.draftState.unassignedDeviceIds.size}"
                    )
                    Log.d(
                        TAG_SESS,
                        "draft.groups=" + s.draftState.groups.joinToString { "${it.groupId}#${it.groupNumber}(devs=${it.deviceIds.size})" }
                    )
                }
            }
        }

        // 2.5) ✅ Коммит 3: единый batch-load устройств для MANUAL (groups + unassigned)
        viewModelScope.launch(ioDispatcher) {
            manualSession
                .map { s ->
                    if (s?.manualModeActive != true) {
                        return@map 0L to emptySet<Long>()
                    }

                    val draft = s.draftState
                    val assigned = draft.groups.asSequence().flatMap { it.deviceIds.asSequence() }
                    val unassigned = draft.unassignedDeviceIds.asSequence()

                    s.version to (assigned + unassigned).toSet()
                }
                .distinctUntilChanged()
                .collectLatest { (sessionVersion, neededIds) ->
                    if (neededIds.isEmpty()) {
                        val version = _manualDevicesRequestVersion.value + 1
                        _manualDevicesRequestVersion.value = version
                        _manualDevicesById.value = emptyMap()
                        Log.d(
                            "MANUAL_DEVICES",
                            "CLEAR neededIds=0 ver=$version sessionVer=$sessionVersion"
                        )
                        return@collectLatest
                    }

                    val version = _manualDevicesRequestVersion.value + 1
                    _manualDevicesRequestVersion.value = version

                    Log.d(
                        "MANUAL_DEVICES",
                        "LOAD start ids=${neededIds.size} ver=$version sessionVer=$sessionVersion"
                    )

                    val devices = deviceRepository.getDevicesByIds(neededIds.toList())
                    val returnedIds = devices.map { it.id }.toSet()
                    val missing = neededIds - returnedIds

                    Log.d(
                        "MANUAL_DEVICES",
                        "LOAD result returned=${returnedIds.size} missing=${missing.size} " +
                                "missingIds=${missing.take(20)} sessionVer=$sessionVersion"
                    )

                    val map = devices.associateBy { it.id }

                    if (_manualDevicesRequestVersion.value == version) {
                        _manualDevicesById.value = map
                        Log.d(
                            "MANUAL_DEVICES",
                            "LOAD apply ids=${map.size} ver=$version sessionVer=$sessionVersion"
                        )
                    } else {
                        Log.w(
                            "MANUAL_DEVICES",
                            "LOAD drop stale ids=${map.size} ver=$version " +
                                    "current=${_manualDevicesRequestVersion.value} sessionVer=$sessionVersion"
                        )
                    }
                }
        }

        viewModelScope.launch {
            activeProjectIdState
                .collect { _lastCreateOpId.value = null }
        }

        // 3) ✅ Главный FIX: uiState строим от БД, когда manual выключен.
        // ✅ Коммит 2: УБРАН DB-triggered auto-recalc.
        // DB pipeline = ТОЛЬКО READ → BUILD UI. Никаких write в БД.
        viewModelScope.launch(ioDispatcher) {

            data class DbGroupsAndMode(
                val groups: List<CircuitGroup>,
                val mode: PhaseMode
            )

            data class DbWithDecisions(
                val groups: List<CircuitGroup>,
                val mode: PhaseMode,
                val decisions: List<DistributionDecision>
            )

            data class DbWithSession(
                val groups: List<CircuitGroup>,
                val mode: PhaseMode,
                val decisions: List<DistributionDecision>,
                val session: ManualEditSession?
            )

            data class DbWithSessionAndManualGroups(
                val base: DbWithSession,
                val manualGroups: List<CircuitGroup>
            )

            data class Snapshot(
                val groups: List<CircuitGroup>,
                val mode: PhaseMode,
                val availablePowerKw: Double?,
                val unassignedDeviceCount: Int,
                val decisions: List<DistributionDecision>,
                val session: ManualEditSession?,
                val manualGroups: List<CircuitGroup>,
                val projectId: String
            )

            val groupsAndModeFlow: Flow<DbGroupsAndMode> =
                dbGroupsFlow.combine(phaseMode) { groups: List<CircuitGroup>, mode: PhaseMode ->
                    DbGroupsAndMode(groups = groups, mode = mode)
                }



            val withDecisionsFlow: Flow<DbWithDecisions> =
                groupsAndModeFlow.combine(decisionsFlow) { gm: DbGroupsAndMode, decisions: List<DistributionDecision> ->
                    DbWithDecisions(
                        groups = gm.groups,
                        mode = gm.mode,
                        decisions = decisions
                    )
                }

            val withSessionFlow: Flow<DbWithSession> =
                withDecisionsFlow.combine(manualSession) { base: DbWithDecisions, s: ManualEditSession? ->
                    DbWithSession(
                        groups = base.groups,
                        mode = base.mode,
                        decisions = base.decisions,
                        session = s
                    )
                }


            // ✅ ВАЖНО: весь snapshot строится внутри flatMapLatest(projectId)
            // => невозможно склеить groups от одного проекта с projectId другого
            val snapshotFlow: Flow<Snapshot> =
                activeProjectIdState
                    .map { it.orEmpty().trim() }
                    .distinctUntilChanged()
                    .flatMapLatest { projectId ->
                        if (projectId.isBlank()) {
                            flowOf(
                                Snapshot(
                                    groups = emptyList(),
                                    mode = phaseMode.value,
                                    availablePowerKw = activeProjectSetup.value?.inputPowerKw,
                                    unassignedDeviceCount = 0,
                                    decisions = emptyList(),
                                    session = null,
                                    manualGroups = emptyList(),
                                    projectId = ""
                                )
                            )
                        } else {
                            // 1) groups — строго от этого projectId
                            val groupsFlow: Flow<List<CircuitGroup>> =
                                repo.observeAllGroupByProject(projectId)
                                    .onStart { emit(emptyList()) } // ✅ мгновенный flush на смене проекта

                            // 2) session — уже scoped у тебя (manualSession), но мы всё равно используем тут как вход
                            // 3) manualGroups — зависят от session/devices, тоже ок
                            // 4) decisions — если у тебя не project-scoped, оно может быть “общим”,
                            //    но теперь хотя бы projectId не склеится с чужими groups
                            combine(
                                groupsFlow,
                                electricalContext,
                                decisionsFlow,
                                manualSession,
                                manualDisplayGroups
                            ) { groups, context, decisions, session, manualGroups ->
                                Snapshot(
                                    groups = groups,
                                    mode = context.phaseMode,
                                    availablePowerKw = context.availablePowerKw,
                                    unassignedDeviceCount = context.unassignedDeviceCount,
                                    decisions = decisions,
                                    session = session,
                                    manualGroups = manualGroups,
                                    projectId = projectId
                                )
                            }
                        }
                    }

            // ✅ Commit 1: visibility flow зависит от lastCreateOpId => лог гарантированно появится ПОСЛЕ add-device
            val uiVisibilityFlow: Flow<Pair<Snapshot, String?>> =
                snapshotFlow.combine(lastCreateOpId) { snap, opId ->
                    snap to opId
                }

            viewModelScope.launch(ioDispatcher) {
                uiVisibilityFlow.collect { (snap, opId) ->
                    val manualActive = snap.session?.manualModeActive == true

                    Log.i(
                        "EXP_UI_VISIBILITY",
                        "thread=${Thread.currentThread().name} " +
                                "projectIdUi=${snap.projectId} " +
                                "groupsCount=${snap.groups.size} " +
                                "manualActive=$manualActive " +
                                "draftUnassignedIdsCount=${snap.session?.draftState?.unassignedDeviceIds?.size ?: 0} " +
                                "lastCreateOpId=$opId"
                    )
                }
            }

            snapshotFlow.collect { snap: Snapshot ->

                val groups = snap.groups
                val mode = snap.mode
                val decisions = snap.decisions
                val session = snap.session
                val manualGroups = snap.manualGroups

                val manualActive = session?.manualModeActive == true

                // ---- BUILD UI STATE (единственный писатель Success) ----
                val plan = userPlanRepository.planFlow.value
                val isProReport = plan.capabilities.professionalReportSections

                // 1) MANUAL: экран строим из draft (manualGroups)
                if (manualActive) {
                    if (manualGroups.isEmpty()) {
                        _uiState.value = GroupScreenState.Empty(
                            mode = GroupScreenState.Empty.EmptyMode.MANUAL,
                            title = "Ручной режим",
                            message = "Группы пока не созданы"
                        )
                    } else {
                        setSuccessFromGroups(
                            groups = manualGroups,
                            mode = mode,
                            isProReport = isProReport,
                            decisions = decisions,
                            availablePowerKw = snap.availablePowerKw,
                            unassignedDeviceCount = snap.unassignedDeviceCount
                        )
                    }
                    return@collect
                }

                // 2) AUTO: если группы есть — это SUCCESS.
                if (groups.isNotEmpty()) {
                    setSuccessFromGroups(
                        groups = groups,
                        mode = mode,
                        isProReport = isProReport,
                        decisions = decisions,
                        availablePowerKw = snap.availablePowerKw,
                        unassignedDeviceCount = snap.unassignedDeviceCount
                    )
                    return@collect
                }

                // 3) AUTO: групп нет — показываем Empty.
                // ✅ Коммит 2: НИКАКОГО auto-recalc / write из DB pipeline.
                _uiState.value = GroupScreenState.Empty(
                    mode = GroupScreenState.Empty.EmptyMode.AUTO,
                    title = "Распределение по фазам",
                    message = "Группы пока не созданы"
                )
            }
        }

        // ✅ Commit 1: ловим add-device события и сохраняем lastCreateOpId
        // ✅ Commit X: защита от дублей (StateFlow может пере-эмитить последнее значение на новую подписку)
        viewModelScope.launch {
            var lastLoggedOpId: String? = null

            createDeviceOpBus.state.collect { e ->
                if (e == null) return@collect

                val activePid = activeProjectIdState.value
                if (!activePid.isNullOrBlank() && e.projectIdRecorded != activePid) {
                    Log.w(
                        "CREATE_DEVICE_UC",
                        "DROP foreign opId=${e.opId} recordedPid=${e.projectIdRecorded} activePid=$activePid"
                    )
                    return@collect
                }

                if (lastLoggedOpId == e.opId) return@collect

                lastLoggedOpId = e.opId
                _lastCreateOpId.value = e.opId

                Log.i(
                    "CREATE_DEVICE_UC",
                    "opId=${e.opId} pidRecorded=${e.projectIdRecorded} roomId=${e.roomId} inserted=${e.insertedIds.size}"
                )
            }
        }
    }

    private fun setSuccessFromGroups(
        groups: List<CircuitGroup>,
        mode: PhaseMode,
        isProReport: Boolean,
        decisions: List<DistributionDecision>,
        availablePowerKw: Double? = activeProjectSetup.value?.inputPowerKw,
        unassignedDeviceCount: Int = projectCoverage.value.unassignedDevices.size
    ) {
        val totalGroups = groups.size
        val hasGroupRcds = groups.any { it.rcdRequired }

        val electricalSummary = runCatching {
            val phaseVector = phaseLoadVector(groups)
            val totalCurrent = when (mode) {
                PhaseMode.SINGLE -> phaseVector.a
                PhaseMode.THREE -> phaseVector.max
            }
            val incomerAssessment = incomerSelector.assess(
                IncomerSelector.Params(
                    groups = groups,
                    preferRcbo = false,
                    hasGroupRcds = hasGroupRcds,
                    availablePowerKw = availablePowerKw,
                    unassignedDeviceCount = unassignedDeviceCount,
                    voltageTypeOverride = when (mode) {
                        PhaseMode.SINGLE -> VoltageType.AC_1PHASE
                        PhaseMode.THREE -> VoltageType.AC_3PHASE
                    }
                )
            )
            Triple(totalCurrent, incomerAssessment.spec, incomerAssessment)
        }.getOrElse { failure ->
            _uiState.value = GroupScreenState.Error(
                failure.message ?: "Не удалось рассчитать параметры щита"
            )
            return
        }
        val (totalCurrent, incomer, incomerAssessment) = electricalSummary

        val totals = calculateShieldOverviewUseCase.execute(groups)
        val installedPower = totals.installedPowerW
        val calculatedPower = totals.calculatedPowerW

        val calcWarningsFromGroups = buildList {
            incomerAssessment.toCalcWarning()?.let(::add)
            if (isProReport) addAll(buildWarningsFromGroups(groups))
        }

        val shieldTotalsAssumptions: List<CalcAssumption> =
            if (isProReport) calculatedPower.assumptions else emptyList()

        val proAssumptions: List<CalcAssumption> =
            if (isProReport) {
                buildList {
                    addAll(installedPower.assumptions)
                    addAll(calculatedPower.assumptions)
                }.distinctBy { it.toString() }
            } else emptyList()

        val proWarnings: List<CalcWarning> =
            if (isProReport) {
                buildList {
                    addAll(calcWarningsFromGroups)
                    addAll(installedPower.warnings)
                    addAll(calculatedPower.warnings)
                }.distinctBy { "${it.severity}|${it.scope}|${it.title}|${it.message}" }
            } else emptyList()

        // ✅ Стабильная дата: если уже был Success — сохраняем прежнюю (чтобы PDF не “прыгал” без причины).
        val reportDate = (uiState.value as? GroupScreenState.Success)?.reportDate
            ?: SimpleDateFormat(
                "dd.MM.yyyy",
                Locale.getDefault()
            ).format(System.currentTimeMillis())

        val (meta, phases) = buildReportDataFromDeterministic(
            groups = groups,
            incomer = incomer,
            incomerAssessment = incomerAssessment,
            totalGroups = totalGroups,
            mode = mode,
            date = reportDate,
            cableCalculations = cableCalculations.value
        )

        val professionalSections: ProfessionalSections? =
            if (isProReport) {
                BuildProfessionalSectionsUseCase().execute(
                    BuildProfessionalSectionsUseCase.Params(
                        phaseMode = mode,
                        meta = meta,
                        phases = phases,
                        distributionDecisions = decisions,
                        calcWarnings = proWarnings,
                        assumptions = proAssumptions
                    )
                )
            } else null

        _uiState.value = GroupScreenState.Success(
            groups = groups,
            totalGroups = totalGroups,
            totalCurrent = totalCurrent,
            incomer = incomer,
            incomerAssessment = incomerAssessment,
            hasGroupRcds = hasGroupRcds,
            installedPowerW = installedPower,
            calculatedPowerW = calculatedPower,
            shieldTotalsAssumptions = shieldTotalsAssumptions,
            calcWarnings = calcWarningsFromGroups,
            professionalSections = professionalSections,
            reportDate = reportDate
        )
    }

    // =========================
    // Unassigned: refresh/clear
    // =========================

    fun onAutoAssignUnassignedClick() {
        viewModelScope.launch(ioDispatcher) {
            val projectId = currentProjectIdOrNull() ?: run {
                Log.w("AUTO_ASSIGN_UI", "click ignored: projectId=null")
                return@launch
            }

            val beforeSession = getManualSessionForProject(projectId)
            if (beforeSession?.manualModeActive != true) {
                Log.w(
                    "AUTO_ASSIGN_UI",
                    "click ignored: manual inactive pid=$projectId hasSession=${beforeSession != null}"
                )
                return@launch
            }

            val beforeIds = beforeSession.draftState.unassignedDeviceIds.toList().sorted()

            Log.i(
                "AUTO_ASSIGN_UI",
                "CLICK pid=$projectId ver=${beforeSession.version} " +
                        "unassignedBefore=${beforeIds.size} idsBefore=$beforeIds"
            )

            try {
                manualRepo.apply(
                    projectId = projectId,
                    action = ManualEditAction.AutoAssignUnassigned
                )

                val afterSession = getManualSessionForProject(projectId)
                val afterIds = afterSession?.draftState?.unassignedDeviceIds
                    ?.toList()
                    ?.sorted()
                    .orEmpty()

                Log.i(
                    "AUTO_ASSIGN_UI",
                    "DONE pid=$projectId verBefore=${beforeSession.version} " +
                            "verAfter=${afterSession?.version} " +
                            "unassignedAfter=${afterIds.size} idsAfter=$afterIds"
                )

                _events.value = UiEvent.ShowSnackbar("Нераспределённые устройства распределены")
            } catch (t: Throwable) {
                Log.e("AUTO_ASSIGN_UI", "FAILED pid=$projectId", t)
                _events.value = UiEvent.ShowSnackbar("Не удалось распределить устройства")
            }
        }
    }

    // =========================
    // PDF
    // =========================

    fun onExportPdfClick() {
        _events.value = UiEvent.ExportPdfRequested
    }

    fun onPdfExportActionsClick() {
        val plan = userPlanRepository.planFlow.value
        if (!plan.capabilities.pdfExport) {
            paywallBus.request(ProFeature.PRO_REPORT)
            return
        }
        _events.value = UiEvent.PdfExportActionsRequested
    }

    /**
     * Клик по кнопке "Однолинейная схема".
     *
     * Важно:
     * - сначала проверяем PRO;
     * - берём только текущий Success-state экспликации;
     * - не читаем DAO повторно;
     * - не запускаем пересчёт групп.
     */
    fun onSingleLineDiagramClick() {
        val plan = userPlanRepository.planFlow.value

        if (!plan.capabilities.pdfExport) {
            paywallBus.request(ProFeature.SINGLE_LINE_DIAGRAM)
            return
        }

        val state = uiState.value as? GroupScreenState.Success

        if (state == null) {
            _events.value = UiEvent.ShowSnackbar(
                "Сначала сформируйте структуру щита в разделе «Экспликация»."
            )
            return
        }

        try {
            val diagram = generateSingleLineDiagramUseCase(
                projectName = "ВольтХом",
                phaseMode = phaseMode.value,
                groups = state.groups,
                incomer = state.incomer,
                totalInstalledPowerWatts = state.installedPowerW.value,
                totalCalculatedPowerWatts = state.calculatedPowerW.value,
                totalCurrentAmps = state.totalCurrent,
                cableCalculations = cableCalculations.value,
                customModules = panelLayoutSnapshot.value?.customModules.orEmpty()
            )
            _events.value = UiEvent.SingleLineDiagramRequested(diagram)
        } catch (t: Throwable) {
            Log.e("SINGLE_LINE_EXPORT", "Failed to build SingleLineDiagram", t)
            _events.value = UiEvent.ShowSnackbar(
                "Не удалось сформировать однолинейную схему. Проверьте данные проекта и попробуйте еще раз."
            )
        }
    }

    fun onPanelVisualizationLockedClick() {
        paywallBus.request(ProFeature.PANEL_VISUALIZATION)
    }

    // =========================
    // Drag actions (manual) — Коммит 1
    // =========================

    fun startDrag(
        deviceId: Long,
        fromGroupId: Long,
        itemStartRoot: Offset,
        pointerStartRoot: Offset
    ) {
        Log.d(
            TAG_DND,
            "startDrag deviceId=$deviceId from=$fromGroupId item=$itemStartRoot pointer=$pointerStartRoot"
        )
        _dragState.value = DragState(
            draggingDeviceId = deviceId,
            fromGroupId = fromGroupId,
            itemStartRoot = itemStartRoot,
            pointerStartRoot = pointerStartRoot,
            pointerCurrentRoot = pointerStartRoot,
            ghostPositionRoot = itemStartRoot,
            activeTarget = null
        )
    }

    fun onResetManualOverridesClick() {
        // защита: в manual нельзя
        val isManual = manualSession.value?.manualModeActive == true
        if (isManual) {
            _events.value = UiEvent.ShowSnackbar("Сначала выйдите из ручного режима")
            return
        }
        _showResetManualConfirmDialog.value = true
    }

    fun onResetManualOverridesDismiss() {
        _showResetManualConfirmDialog.value = false
    }

    fun onResetManualOverridesConfirm() {
        viewModelScope.launch(ioDispatcher) {
            _showResetManualConfirmDialog.value = false

            val projectId = activeProjectIdState.value.orEmpty().trim()
            if (projectId.isBlank()) {
                _events.value = UiEvent.ShowSnackbar("Не выбран проект")
                return@launch
            }

            // manual сейчас выключен — ок
            _isRecalculating.value = true
            _uiState.value = GroupScreenState.Loading

            try {
                val mode = phaseMode.value

                Log.w("MANUAL_RESET", "RESET START pid=$projectId mode=$mode")

                when (val res = resetManualOverridesAndAutoRecalcUseCase.execute(
                    ResetManualOverridesAndAutoRecalcUseCase.Params(
                        projectId = projectId,
                        phaseMode = mode
                    )
                )) {
                    is GroupingResult.Error -> {
                        Log.e("MANUAL_RESET", "RESET FAILED pid=$projectId msg=${res.message}")
                        _uiState.value = GroupScreenState.Error(res.message)
                        _events.value = UiEvent.ShowSnackbar("Не удалось сбросить: ${res.message}")
                    }

                    is GroupingResult.Success -> {
                        Log.w(
                            "MANUAL_RESET",
                            "RESET OK pid=$projectId groups=${res.system.groups.size}"
                        )
                        repo.setLastDistributionDecisions(res.distributionDecisions)
                        _events.value = UiEvent.ShowSnackbar("Ручные изменения сброшены")
                        // uiState сам восстановится из DB pipeline
                    }
                }
            } catch (t: Throwable) {
                Log.e("MANUAL_RESET", "RESET EXCEPTION pid=$projectId", t)
                _uiState.value = GroupScreenState.Error("Ошибка сброса ручных изменений")
                _events.value = UiEvent.ShowSnackbar("Ошибка сброса ручных изменений")
            } finally {
                _isRecalculating.value = false
            }
        }
    }

    fun setActiveDragTarget(target: DragTarget?) {
        val s = _dragState.value
        if (!s.isActive) {
            Log.w(TAG_DND, "setActiveDragTarget ignored: not active target=$target")
            return
        }
        Log.d(TAG_DND, "setActiveDragTarget $target")
        if (s.activeTarget == target) return
        _dragState.value = s.copy(activeTarget = target)
    }

    fun updateDrag(pointerRoot: Offset) {
        val s = _dragState.value
        if (!s.isActive) {
            Log.w(TAG_DND, "updateDrag ignored: not active")
            return
        }
        Log.v(
            TAG_DND,
            "updateDrag pointer=$pointerRoot startPointer=${s.pointerStartRoot} startItem=${s.itemStartRoot}"
        )

        val startPointer = s.pointerStartRoot ?: return
        val startItem = s.itemStartRoot ?: return

        val delta = pointerRoot - startPointer
        val ghostPos = startItem + delta

        _dragState.value = s.copy(
            pointerCurrentRoot = pointerRoot,
            ghostPositionRoot = ghostPos
        )
    }

    fun cancelDrag() {
        Log.d("DRAG_TRACE", "VM cancelDrag -> reset dragState only (panel stays)")
        _dragState.value = DragState()
    }

    fun dropToGroup(targetGroupId: Long) {
        Log.d("DRAG_TRACE", "VM dropToGroup target=$targetGroupId state=$_dragState.value")

        val s = _dragState.value
        Log.d(TAG_DND, "dropToGroup target=$targetGroupId state=$s")
        val deviceId = s.draggingDeviceId ?: return
        val fromGroupId = s.fromGroupId ?: return

        _moveDeviceUi.value = MoveDeviceUi(deviceId = deviceId, fromGroupId = fromGroupId)

        onMoveDeviceTargetGroupSelected(deviceId = deviceId, targetGroupId = targetGroupId)

        _dragState.value = DragState()
    }

    fun dropToUnassigned() {
        val s = _dragState.value
        val deviceId = s.draggingDeviceId ?: return
        val fromGroupId = s.fromGroupId ?: return

        _moveDeviceUi.value = MoveDeviceUi(deviceId = deviceId, fromGroupId = fromGroupId)
        onMoveDeviceToUnassignedSelected(deviceId = deviceId)

        _dragState.value = DragState()
    }

    fun dropCancel() {
        Log.d(TAG_DND, "dropCancel state=${_dragState.value}")
        _dragState.value = DragState()
        _moveDeviceUi.value = null
    }

    fun dropToNewGroup() {
        val s = _dragState.value
        val deviceId = s.draggingDeviceId ?: return
        val fromGroupId = s.fromGroupId ?: return

        _moveDeviceUi.value = MoveDeviceUi(deviceId = deviceId, fromGroupId = fromGroupId)
        onMoveDeviceToNewGroupSelected(deviceId = deviceId)

        _dragState.value = DragState()
    }

    // =========================
    // Move device UI (manual)
    // =========================

    fun onDeviceLongPressed(deviceId: Long, fromGroupId: Long) {
        Log.d(TAG_MOVE, "onDeviceLongPressed deviceId=$deviceId fromGroupId=$fromGroupId")
        _moveDeviceUi.value = MoveDeviceUi(deviceId = deviceId, fromGroupId = fromGroupId)
    }

    fun dismissMoveDevice() {
        Log.d(TAG_MOVE, "dismissMoveDevice -> close panel + reset dragState")

        _moveDeviceUi.value = null
        _dragState.value = DragState()
    }

    fun onMoveDeviceTargetGroupSelected(deviceId: Long, targetGroupId: Long) {
        val from = _moveDeviceUi.value?.fromGroupId
        _moveDeviceUi.value = null

        viewModelScope.launch(ioDispatcher) {
            val projectId = currentProjectIdOrNull() ?: run {
                Log.e("MOVE_DEBUG", "projectId=null")
                _events.value = UiEvent.ShowSnackbar("Не выбран проект")
                return@launch
            }

            val session = getManualSessionForProject(projectId)
            if (session == null) {
                Log.e("MOVE_DEBUG", "session=null pid=$projectId")
                _events.value = UiEvent.ShowSnackbar("Manual session = null")
                return@launch
            }

            if (!session.manualModeActive) {
                Log.e("MOVE_DEBUG", "manualModeActive=false pid=$projectId")
                _events.value = UiEvent.ShowSnackbar("Manual mode inactive")
                return@launch
            }

            val fromGroupId = from ?: run {
                Log.e("MOVE_DEBUG", "fromGroupId=null")
                return@launch
            }

            Log.d(
                "DRAG_TRACE",
                "VM APPLY MOVE pid=$projectId device=$deviceId from=$fromGroupId to=$targetGroupId"
            )

            val action = if (fromGroupId == FROM_UNASSIGNED) {
                ManualEditAction.MoveFromUnassigned(
                    deviceId = deviceId,
                    toGroupId = targetGroupId
                )
            } else {
                ManualEditAction.MoveDevice(
                    deviceId = deviceId,
                    fromGroupId = fromGroupId,
                    toGroupId = targetGroupId
                )
            }

            Log.d("DRAG_TRACE", "VM APPLY pid=$projectId action=$action")
            try {
                manualRepo.apply(projectId = projectId, action = action)
            } catch (t: IllegalArgumentException) {
                Log.w("DRAG_TRACE", "VM REJECTED pid=$projectId action=$action", t)
                _events.value = UiEvent.ShowSnackbar(
                    t.message
                        ?.substringAfter(": ")
                        ?.takeIf(String::isNotBlank)
                        ?: "Такое объединение устройств недопустимо"
                )
            } catch (t: Throwable) {
                Log.e("DRAG_TRACE", "VM FAILED pid=$projectId action=$action", t)
                _events.value = UiEvent.ShowSnackbar("Не удалось переместить устройство")
            }
        }
    }


    fun onMoveDeviceToNewGroupSelected(deviceId: Long) {
        _moveDeviceUi.value = null

        viewModelScope.launch(ioDispatcher) {
            val projectId = currentProjectIdOrNull() ?: run {
                _events.value = UiEvent.ShowSnackbar("Не выбран проект")
                return@launch
            }

            val session = getManualSessionForProject(projectId)
            if (session == null) {
                Log.d(
                    TAG_SESS,
                    "onMoveDeviceToNewGroupSelected: session=null pid=$projectId deviceId=$deviceId"
                )
                _events.value = UiEvent.ShowSnackbar("Сессия ручного режима недоступна")
                return@launch
            }

            if (!session.manualModeActive) {
                Log.w(
                    TAG_SESS,
                    "onMoveDeviceToNewGroupSelected: manualModeActive=false pid=$projectId deviceId=$deviceId"
                )
                _events.value = UiEvent.ShowSnackbar("Ручной режим выключен")
                return@launch
            }

            Log.d(
                TAG_MOVE,
                "toNewGroup deviceId=$deviceId pid=$projectId ver=${session.version}"
            )

            try {
                manualRepo.apply(
                    projectId = projectId,
                    action = ManualEditAction.CreateNewGroupAndMove(deviceId = deviceId)
                )
            } catch (t: IllegalArgumentException) {
                Log.w(TAG_MOVE, "toNewGroup rejected deviceId=$deviceId pid=$projectId", t)
                _events.value = UiEvent.ShowSnackbar(
                    t.message?.substringAfter(": ")
                        ?: "Для устройства нельзя создать такую группу"
                )
            } catch (t: Throwable) {
                Log.e(TAG_MOVE, "toNewGroup failed deviceId=$deviceId pid=$projectId", t)
                _events.value = UiEvent.ShowSnackbar("Не удалось создать новую группу")
            }
        }
    }

    fun onMoveDeviceToUnassignedSelected(deviceId: Long) {
        val fromGroupId = _moveDeviceUi.value?.fromGroupId
        _moveDeviceUi.value = null

        viewModelScope.launch(ioDispatcher) {
            val projectId = currentProjectIdOrNull() ?: run {
                _events.value = UiEvent.ShowSnackbar("Не выбран проект")
                return@launch
            }

            val session = getManualSessionForProject(projectId)
            if (session == null) {
                Log.w(
                    TAG_SESS,
                    "onMoveDeviceToUnassignedSelected: session=null pid=$projectId deviceId=$deviceId from=$fromGroupId"
                )
                _events.value = UiEvent.ShowSnackbar("Сессия ручного режима недоступна")
                return@launch
            }

            if (!session.manualModeActive) {
                Log.w(
                    TAG_SESS,
                    "onMoveDeviceToUnassignedSelected: manualModeActive=false pid=$projectId deviceId=$deviceId"
                )
                _events.value = UiEvent.ShowSnackbar("Ручной режим выключен")
                return@launch
            }

            val from = fromGroupId ?: run {
                Log.w(TAG_MOVE, "toUnassigned: fromGroupId=null deviceId=$deviceId")
                _events.value = UiEvent.ShowSnackbar("Не удалось определить исходную группу")
                return@launch
            }

            Log.d(
                TAG_MOVE,
                "toUnassigned deviceId=$deviceId from=$from pid=$projectId ver=${session.version}"
            )

            try {
                manualRepo.apply(
                    projectId = projectId,
                    action = ManualEditAction.MoveToUnassigned(
                        deviceId = deviceId,
                        fromGroupId = from
                    )
                )
            } catch (t: Throwable) {
                Log.e(
                    TAG_MOVE,
                    "toUnassigned failed deviceId=$deviceId from=$from pid=$projectId",
                    t
                )
                _events.value = UiEvent.ShowSnackbar("Не удалось переместить в нераспределённые")
            }
        }
    }

    // =========================
    // BottomSheet
    // =========================

    fun openInfoSheet(payload: InfoSheetPayload) {
        _infoSheetPayload.value = payload
    }

    fun closeInfoSheet() {
        _infoSheetPayload.value = null
    }

    // =========================
    // Group/Shield clicks
    // =========================

    fun onGroupPowerClick(group: CircuitGroup) {
        val plan = userPlanRepository.planFlow.value
        val hasAccess = plan.capabilities.professionalReportSections

        val breakdown = calculateGroupBreakdownUseCase.execute(group)
        val calculated = breakdown.calculatedPower

        val steps = calculated.steps
        val calcDetailsState = resolveCalcDetailsState(
            hasSteps = steps.isNotEmpty(),
            hasAccess = hasAccess
        )

        openInfoSheet(
            InfoSheetPayload(
                title = "Мощность группы",
                sheetType = InfoSheetType.CALCULATION,
                calcDetailsState = calcDetailsState,
                currentValueText = "%.2f кВт".format(calculated.value / 1000.0),
                calcBlocks = if (hasAccess) CalcBlocksMapper.mapSteps(steps) else emptyList(),
                calculationStory = if (hasAccess) {
                    CalculationStoryMapper.mapGroup(
                        steps = steps,
                        kind = GroupCalculationKind.POWER
                    )
                } else {
                    null
                },
                normRefs = emptyList()
            )
        )
    }

    fun onGroupCurrentClick(group: CircuitGroup) {
        val plan = userPlanRepository.planFlow.value
        val hasAccess = plan.capabilities.professionalReportSections

        val breakdown = calculateGroupBreakdownUseCase.execute(group)
        val calculated = breakdown.calculatedCurrent

        val steps = calculated.steps
        val calcDetailsState = resolveCalcDetailsState(
            hasSteps = steps.isNotEmpty(),
            hasAccess = hasAccess
        )

        openInfoSheet(
            InfoSheetPayload(
                title = "Расчётный ток",
                sheetType = InfoSheetType.CALCULATION,
                calcDetailsState = calcDetailsState,
                currentValueText = "%.2f А".format(calculated.value),
                calcBlocks = if (hasAccess) CalcBlocksMapper.mapSteps(steps) else emptyList(),
                calculationStory = if (hasAccess) {
                    CalculationStoryMapper.mapGroup(
                        steps = steps,
                        kind = GroupCalculationKind.CURRENT
                    )
                } else {
                    null
                },
                normRefs = emptyList()
            )
        )
    }

    fun onDeviceClick(deviceId: Long) {
        viewModelScope.launch(ioDispatcher) {
            _selectedDeviceBreakdown.value = null

            val dev = deviceRepository.getDeviceById(deviceId.toInt())
            _selectedDevice.value = dev

            val plan = userPlanRepository.planFlow.value
            if (plan.capabilities.professionalReportSections) {
                _selectedDeviceBreakdown.value =
                    dev?.let { calculateDeviceBreakdownUseCase.execute(it) }
            }
        }
    }

    fun onInstalledPowerClick(calculated: CalculatedValue) {
        val plan = userPlanRepository.planFlow.value
        val hasAccess = plan.capabilities.professionalReportSections

        val steps = calculated.steps
        val calcDetailsState = resolveCalcDetailsState(
            hasSteps = steps.isNotEmpty(),
            hasAccess = hasAccess
        )

        openInfoSheet(
            InfoSheetPayload(
                title = "Установленная мощность",
                sheetType = InfoSheetType.CALCULATION,
                calcDetailsState = calcDetailsState,
                currentValueText = "%.1f кВт".format(calculated.value / 1000.0),
                calcBlocks = if (hasAccess) CalcBlocksMapper.mapSteps(steps) else emptyList(),
                calculationStory = if (hasAccess) {
                    CalculationStoryMapper.mapInstalledPower(steps)
                } else {
                    null
                },
                normRefs = if (hasAccess) calculated.normRefs.map { it.toString() } else emptyList()
            )
        )
    }

    fun onCalculatedPowerClick(calculated: CalculatedValue) {
        val plan = userPlanRepository.planFlow.value
        val hasAccess = plan.capabilities.professionalReportSections

        val steps = calculated.steps
        val calcDetailsState = resolveCalcDetailsState(
            hasSteps = steps.isNotEmpty(),
            hasAccess = hasAccess
        )

        openInfoSheet(
            InfoSheetPayload(
                title = "Расчётная нагрузка",
                sheetType = InfoSheetType.CALCULATION,
                calcDetailsState = calcDetailsState,
                currentValueText = "%.1f кВт".format(calculated.value / 1000.0),
                calcBlocks = if (hasAccess) CalcBlocksMapper.mapSteps(steps) else emptyList(),
                calculationStory = if (hasAccess) {
                    CalculationStoryMapper.mapGroup(
                        steps = steps,
                        kind = GroupCalculationKind.POWER,
                        resultLabelOverride = "Расчётная нагрузка щита",
                        comparisonLabelOverride = "Установленная мощность щита",
                        interpretationOverride =
                            "Это значение используется для оценки нагрузки объекта, распределения по фазам и предварительного выбора вводного аппарата."
                    )
                } else {
                    null
                },
                normRefs = if (hasAccess) calculated.normRefs.map { it.toString() } else emptyList()
            )
        )
    }

    fun onIncomerFieldClick(
        field: InfoSheetPayloadFactory.IncomerField,
        incomer: IncomerSpec,
        hasGroupRcds: Boolean,
        baseCurrentA: Double
    ) {
        openInfoSheet(
            InfoSheetPayloadFactory.incomerField(
                field = field,
                incomer = incomer,
                phaseMode = phaseMode.value,
                hasGroupRcds = hasGroupRcds,
                baseCurrentA = baseCurrentA
            )
        )
    }

    private fun resolveCalcDetailsState(
        hasSteps: Boolean,
        hasAccess: Boolean
    ): CalcDetailsState = when {
        !hasSteps -> CalcDetailsState.NONE
        hasAccess -> CalcDetailsState.AVAILABLE
        else -> CalcDetailsState.LOCKED
    }

    // =========================
    // Recalc (auto) — ТОЛЬКО user intent
    // =========================

    fun recalcAndSaveGroups() {
        viewModelScope.launch(ioDispatcher) {

            val projectId = activeProjectIdState.value.orEmpty().trim()
            if (projectId.isBlank()) {
                Log.w(
                    "AUTO_GATE",
                    "AUTO_RECALC_ABORT reason=NO_ACTIVE_PROJECT (keep current uiState)"
                )
                return@launch
            }

            val activeSession = getManualSessionForProject(projectId)
            val isManual = activeSession?.manualModeActive == true

            var manualLock = runCatching { projectOwnershipRepository.isManualLock(projectId) }
                .getOrElse {
                    Log.e("AUTO_GATE", "AUTO_RECALC_LOCK_READ_FAILED pid=$projectId", it)
                    false
                }

            // ✅ Новый safety-net:
            // если manual сессии нет, но lock=true, пробуем снять залипший lock
            // ТОЛЬКО когда проект реально пустой по группам.
            if (!isManual && manualLock) {
                Log.w(
                    "AUTO_GATE",
                    "AUTO_RECALC_STALE_LOCK_CHECK pid=$projectId " +
                            "manualActive=false manualLock=true"
                )

                val recovered = recoverStaleManualLockIfProjectEmpty(projectId)

                manualLock = runCatching { projectOwnershipRepository.isManualLock(projectId) }
                    .getOrElse {
                        Log.e("AUTO_GATE", "AUTO_RECALC_LOCK_REREAD_FAILED pid=$projectId", it)
                        true
                    }

                Log.w(
                    "AUTO_GATE",
                    "AUTO_RECALC_STALE_LOCK_RESULT pid=$projectId " +
                            "recovered=$recovered manualLockAfterRecovery=$manualLock"
                )
            }

            if (isManual || manualLock) {
                Log.w(
                    "AUTO_GATE",
                    "AUTO_RECALC_BLOCKED reason=EXPLICIT_RECALC_REQUEST " +
                            "manualActive=$isManual manualLock=$manualLock pid=$projectId ver=${activeSession?.version}"
                )

                restoreUiFromCurrentLocalState()
                _events.value = UiEvent.ShowSnackbar(
                    "Проект заблокирован ручными изменениями. Сначала сбросьте ручной режим."
                )
                return@launch
            }

            if (autoRecalcInFlight.value) {
                Log.w("AUTO_GATE", "AUTO_RECALC_REJECT reason=IN_FLIGHT pid=$projectId")
                return@launch
            }

            autoRecalcInFlight.value = true
            try {
                recalcAndSaveGroupsInternal(
                    projectId = projectId,
                    reason = "EXPLICIT_RECALC_REQUEST",
                    updateUiSuccess = false
                )
            } finally {
                autoRecalcInFlight.value = false
            }
        }
    }

    private suspend fun recalcAndSaveGroupsInternal(
        projectId: String,
        reason: String,
        updateUiSuccess: Boolean
    ) {
        var manualLockBeforeStart =
            runCatching { projectOwnershipRepository.isManualLock(projectId) }
                .getOrElse {
                    Log.e("AUTO_GATE", "AUTO_RECALC_LOCK_READ_FAILED pid=$projectId", it)
                    false
                }

        // ✅ Повторный safety-net на внутреннем контуре,
        // если lock успел долететь сюда до старта пересчёта.
        if (manualLockBeforeStart) {
            val recovered = recoverStaleManualLockIfProjectEmpty(projectId)

            manualLockBeforeStart =
                runCatching { projectOwnershipRepository.isManualLock(projectId) }
                    .getOrElse {
                        Log.e(
                            "AUTO_GATE",
                            "AUTO_RECALC_PRESTART_LOCK_REREAD_FAILED pid=$projectId",
                            it
                        )
                        true
                    }

            Log.w(
                "AUTO_GATE",
                "AUTO_RECALC_PRESTART_STALE_LOCK_RESULT pid=$projectId " +
                        "recovered=$recovered manualLockAfterRecovery=$manualLockBeforeStart"
            )
        }

        if (manualLockBeforeStart) {
            Log.w(
                "AUTO_GATE",
                "AUTO_RECALC_ABORT reason=$reason manualLock=true pid=$projectId stage=BEFORE_LOADING"
            )
            restoreUiFromCurrentLocalState()
            _events.value = UiEvent.ShowSnackbar(
                "Проект заблокирован ручными изменениями. Автопересчёт отменён."
            )
            return
        }

        Log.w("AUTO_GATE", "AUTO_RECALC_ALLOWED reason=$reason pid=$projectId")

        _isRecalculating.value = true
        _uiState.value = GroupScreenState.Loading

        try {
            val mode = projectSetupRepository.resolve(
                projectId = projectId,
                legacyFallbackPhaseMode = preferencesRepository.phaseMode.first()
            ).phaseMode

            Log.w("AUTO_TRIGGER", "AUTO_RECALC_START reason=$reason pid=$projectId mode=$mode")

            val calc = groupCalculatorFactory.create(projectId)
            Log.e("CALC_TRACE", "calculateGroups START pid=$projectId mode=$mode")
            when (val res = calc.calculateGroups(mode)) {
                is GroupingResult.Error -> {
                    Log.e(
                        "CALC_TRACE",
                        "calculateGroups ERROR pid=$projectId message='${res.message}'"
                    )
                    _uiState.value = GroupScreenState.Error(res.message)
                }

                is GroupingResult.Success -> {
                    val groups = res.system.groups
                    Log.e(
                        "CALC_TRACE",
                        "calculateGroups SUCCESS pid=$projectId groups=${groups.size}"
                    )

                    val ids = groups.map { it.groupId }
                    val dup = ids.groupBy { it }.filter { it.value.size > 1 }.keys

                    Log.e(
                        "AUTO_SAVE_TRACE",
                        "about to save pid=$projectId mode=$mode groups=${groups.size} " +
                                "idsSample=${ids.take(20)} dup=$dup"
                    )

                    var manualLockBeforeSave =
                        runCatching { projectOwnershipRepository.isManualLock(projectId) }
                            .getOrElse {
                                Log.e(
                                    "AUTO_SAVE_TRACE",
                                    "manualLock read failed pid=$projectId",
                                    it
                                )
                                false
                            }

                    // ✅ Финальный safety-net:
                    // если lock внезапно true, но проект пустой был и recovery возможен —
                    // пробуем снять перед save.
                    if (manualLockBeforeSave) {
                        val recovered = recoverStaleManualLockIfProjectEmpty(projectId)

                        manualLockBeforeSave =
                            runCatching { projectOwnershipRepository.isManualLock(projectId) }
                                .getOrElse {
                                    Log.e(
                                        "AUTO_SAVE_TRACE",
                                        "manualLock re-read failed pid=$projectId",
                                        it
                                    )
                                    true
                                }

                        Log.w(
                            "AUTO_TRIGGER",
                            "AUTO_RECALC_BEFORE_SAVE_STALE_LOCK_RESULT pid=$projectId " +
                                    "recovered=$recovered manualLockAfterRecovery=$manualLockBeforeSave"
                        )
                    }

                    if (manualLockBeforeSave) {
                        Log.w(
                            "AUTO_TRIGGER",
                            "AUTO_RECALC_SUPPRESSED pid=$projectId groups=${groups.size} " +
                                    "manualLock=true stage=BEFORE_SAVE"
                        )
                        restoreUiFromCurrentLocalState()
                        _events.value = UiEvent.ShowSnackbar(
                            "Проект заблокирован ручными изменениями. Результат автопересчёта не сохранён."
                        )
                        return
                    }

                    saveAutoCalculatedGroupsToLocalDbUseCase.execute(
                        SaveAutoCalculatedGroupsToLocalDbUseCase.Params(
                            projectId = projectId,
                            groups = groups,
                            distributionDecisions = res.distributionDecisions,
                            source = "ExplicationViewModel.recalcAndSaveGroupsInternal",
                            opId = null
                        )
                    )

                    repo.setLastDistributionDecisions(res.distributionDecisions)

                    Log.w(
                        "AUTO_TRIGGER",
                        "AUTO_RECALC_OK pid=$projectId groups=${groups.size} (uiSuccess=$updateUiSuccess)"
                    )

                    // Даже если updateUiSuccess=false, экран нельзя оставлять в Loading.
                    // При успешном save DB pipeline сам поднимет Success/Empty.
                    // Но если эмит не пришёл мгновенно, делаем fail-safe restore.
                    if (_uiState.value is GroupScreenState.Loading) {
                        restoreUiFromCurrentLocalState()
                    }

                    if (updateUiSuccess) {
                        Log.w("AUTO_TRIGGER", "updateUiSuccess=true is not used сейчас")
                    }
                }
            }
        } catch (t: Throwable) {
            _uiState.value = GroupScreenState.Error(t.message ?: "Неизвестная ошибка")
        } finally {
            _isRecalculating.value = false
        }
    }

    // =========================
    // PDF snapshot
    // =========================

    fun buildReportSnapshotForPdf(): PdfReportSnapshot? {
        val s = uiState.value as? GroupScreenState.Success ?: return null
        val mode = phaseMode.value

        val (meta, phases) = buildReportDataFromDeterministic(
            groups = s.groups,
            incomer = s.incomer,
            incomerAssessment = s.incomerAssessment,
            totalGroups = s.totalGroups,
            mode = mode,
            date = s.reportDate,
            cableCalculations = cableCalculations.value
        )

        return PdfReportSnapshot(
            meta = meta,
            phases = phases,
            installedPowerW = s.installedPowerW.value,
            calculatedPowerW = s.calculatedPowerW.value
        )
    }

    /**
     * Коммит 2: triggerAutoRecalc оставлен только как утилита для явных вызовов (если понадобится),
     * но НИГДЕ в DB pipeline он не используется.
     *
     * Сейчас в проекте можно удалить этот метод полностью и вызывать recalcAndSaveGroups().
     */
    private fun triggerAutoRecalc(projectId: String, reason: String) {
        viewModelScope.launch(ioDispatcher) {

            if (autoRecalcInFlight.value) {
                Log.w(
                    "AUTO_TRIGGER",
                    "REJECT auto recalc: already in flight pid=$projectId reason=$reason"
                )
                return@launch
            }

            autoRecalcInFlight.value = true
            try {
                recalcAndSaveGroupsInternal(
                    projectId = projectId,
                    reason = reason,
                    updateUiSuccess = false
                )
            } finally {
                autoRecalcInFlight.value = false
            }
        }
    }

    /**
     * Восстанавливает terminal UI state из текущего локального состояния,
     * не инициируя новых write-операций.
     *
     * Нужен как fail-safe, когда explicit auto recalc был запущен,
     * но сохранить результат нельзя (например, из-за manualLock=true).
     */
    private fun restoreUiFromCurrentLocalState() {
        val session = manualSession.value
        val manualActive = session?.manualModeActive == true
        val manualGroups = manualDisplayGroups.value
        val dbGroups = dbGroupsFlow.value
        val mode = phaseMode.value
        val decisions = decisionsFlow.value

        val plan = userPlanRepository.planFlow.value
        val isProReport = plan.capabilities.professionalReportSections

        when {
            manualActive && manualGroups.isNotEmpty() -> {
                setSuccessFromGroups(
                    groups = manualGroups,
                    mode = mode,
                    isProReport = isProReport,
                    decisions = decisions
                )
            }

            manualActive && manualGroups.isEmpty() -> {
                _uiState.value = GroupScreenState.Empty(
                    mode = GroupScreenState.Empty.EmptyMode.MANUAL,
                    title = "Ручной режим",
                    message = "Группы пока не созданы"
                )
            }

            dbGroups.isNotEmpty() -> {
                setSuccessFromGroups(
                    groups = dbGroups,
                    mode = mode,
                    isProReport = isProReport,
                    decisions = decisions
                )
            }

            else -> {
                _uiState.value = GroupScreenState.Empty(
                    mode = GroupScreenState.Empty.EmptyMode.AUTO,
                    title = "Распределение по фазам",
                    message = "Группы пока не созданы"
                )
            }
        }
    }
}

// =========================
// Screen state
// =========================

sealed class GroupScreenState {
    data object Loading : GroupScreenState()

    data class Empty(
        val mode: EmptyMode,
        val title: String,
        val message: String
    ) : GroupScreenState() {
        enum class EmptyMode { AUTO, MANUAL }
    }

    data class Success(
        val groups: List<CircuitGroup>,
        val totalGroups: Int,
        val totalCurrent: Double,
        val incomer: IncomerSpec,
        val incomerAssessment: IncomerAssessment,
        val hasGroupRcds: Boolean,
        val installedPowerW: CalculatedValue,
        val calculatedPowerW: CalculatedValue,
        val shieldTotalsAssumptions: List<CalcAssumption> = emptyList(),
        val calcWarnings: List<CalcWarning> = emptyList(),
        val professionalSections: ProfessionalSections? = null,
        val reportDate: String
    ) : GroupScreenState()

    data class Error(val message: String) : GroupScreenState()
}

// =========================
// Helpers: warnings
// =========================

private fun buildWarningsFromGroups(groups: List<CircuitGroup>): List<CalcWarning> {
    val warnings = mutableListOf<CalcWarning>()

    groups.forEach { g ->
        g.devices.forEach { d ->
            // Важно: Device.power в доменной модели = Int (non-null)
            if (d.deviceType == DeviceType.LIGHTING && d.power >= 1000) {
                warnings += CalcWarning(
                    severity = CalcWarning.Severity.WARNING,
                    scope = "device:${d.id}",
                    title = "Аномальная мощность освещения",
                    message = "Освещение '${d.name}': ${d.power}W"
                )
            }
        }
    }

    return warnings
}

// =========================
// Helpers: deterministic report build
// =========================

private fun buildReportDataFromDeterministic(
    groups: List<CircuitGroup>,
    incomer: IncomerSpec,
    incomerAssessment: IncomerAssessment,
    totalGroups: Int,
    mode: PhaseMode,
    date: String,
    cableCalculations: Map<Long, CableLineCalculation> = emptyMap()
): Pair<ReportMeta, List<ReportPhase>> {

    val perPhase = phaseCurrents(groups)

    val headlineCurrents: Map<String, Double> = when (mode) {
        PhaseMode.THREE -> mapOf(
            "A" to perPhase.getOrZero(Phase.A),
            "B" to perPhase.getOrZero(Phase.B),
            "C" to perPhase.getOrZero(Phase.C)
        )

        PhaseMode.SINGLE -> mapOf(
            "A" to perPhase.getOrZero(Phase.A)
        )
    }

    val donut: DonutModel = when (mode) {
        PhaseMode.THREE -> DonutModel.PhaseDistribution(valuesA = perPhase)
        PhaseMode.SINGLE -> {
            val usedA = perPhase.getOrZero(Phase.A)
            val limitA = incomer.mcbRating.toDouble()
            DonutModel.IncomerLoad(usedA = usedA, limitA = limitA)
        }
    }

    val meta = ReportMeta(
        date = date,
        incomerLabel = with(incomer) {
            buildString {
                append(kind.name)
                append(", ")
                append("${poles}P, ")
                append("${mcbRating} А, характеристика ${mcbCurve}, Icn ${icn}")
                rcdType?.let { append(", УЗО типа $it, ${rcdSensitivityMa ?: 30} мА") }
                when (incomerAssessment.status) {
                    ru.mugalimov.volthome.domain.model.incomer.IncomerAssessmentStatus.WITHIN_AVAILABLE_POWER -> Unit
                    ru.mugalimov.volthome.domain.model.incomer.IncomerAssessmentStatus.PRELIMINARY ->
                        append(" • предварительно: мощность ввода не указана")
                    ru.mugalimov.volthome.domain.model.incomer.IncomerAssessmentStatus.LOAD_EXCEEDS_AVAILABLE_POWER ->
                        append(" • нагрузка превышает доступную мощность")
                    ru.mugalimov.volthome.domain.model.incomer.IncomerAssessmentStatus.INCOMPLETE_PROJECT,
                    ru.mugalimov.volthome.domain.model.incomer.IncomerAssessmentStatus.REQUIRED_RATING_UNSUPPORTED ->
                        append(" • номинал требует проверки")
                }
            }
        },
        headlineCurrents = headlineCurrents,
        donut = donut,
        totalGroups = totalGroups
    )

    fun phaseKeys(m: PhaseMode): List<Phase> = when (m) {
        PhaseMode.THREE -> listOf(Phase.A, Phase.B, Phase.C)
        PhaseMode.SINGLE -> listOf(Phase.A)
    }

    fun deviceKey(d: Device): String = d.name.trim().lowercase(Locale.ROOT)

    val groupedByPhase: Map<Phase, List<CircuitGroup>> =
        groups.groupBy { it.phase ?: Phase.A }

    val singlePhaseSections = phaseKeys(mode).map { phase ->
        val phaseGroups = groupedByPhase[phase].orEmpty()

        ReportPhase(
            name = "Фаза ${phase.name}",
            totalCurrentA = phaseGroups.sumOf { it.nominalCurrent },
            installedPowerW = phaseGroups.sumOf { it.installedPowerW },
            groups = phaseGroups
                .sortedBy { it.groupNumber }
                .map { g ->
                    ReportGroup(
                        title = "Группа №${g.groupNumber} — ${g.roomName}",
                        number = g.groupNumber,
                        roomName = g.roomName,
                        purpose = g.groupType.toReportPurpose(),
                        phaseLabel = phase.name,
                        installedPowerW = g.installedPowerW,
                        installedCurrentA = CircuitLoadCalculator.calculate(g.devices).installedCurrentA,
                        calculatedCurrentA = g.nominalCurrent,
                        manualNotes = g.manualDeviationCodes.map(::manualDeviationNote),
                        switchLabel = GroupMetaFormatter.buildSwitchLabel(g),
                        rcdLabel = g.fullRcdLabel(),
                        cableLabel = cableCalculations[g.groupId]?.reportCableLabel()
                            ?: GroupMetaFormatter.buildCableLabel(g),
                        devices = g.devices
                            .sortedBy { deviceKey(it) }
                            .map { d ->
                                val normalized = PowerCurrentNormalizer.ensurePAndI(
                                    powerW = d.power.takeIf { it > 0 },
                                    currentA = d.calculateCurrent().takeIf { it > 0.0 },
                                    voltage = d.voltage,
                                    powerFactor = d.powerFactor
                                )
                                ReportDevice(
                                    name = d.name,
                                    powerW = normalized.powerW,
                                    currentA = normalized.currentA
                                )
                            }
                    )
                }
        )
    }

    val threePhaseGroups = groupedByPhase[Phase.THREE_PHASE].orEmpty()
    val threePhaseSection = if (mode == PhaseMode.THREE && threePhaseGroups.isNotEmpty()) {
        listOf(
            ReportPhase(
                name = "Трёхфазные линии",
                totalCurrentA = threePhaseGroups.sumOf { it.nominalCurrent },
                installedPowerW = threePhaseGroups.sumOf { it.installedPowerW },
                groups = threePhaseGroups
                    .sortedBy { it.groupNumber }
                    .map { g ->
                        ReportGroup(
                            title = "Группа №${g.groupNumber} — ${g.roomName}",
                            number = g.groupNumber,
                            roomName = g.roomName,
                            purpose = g.groupType.toReportPurpose(),
                            phaseLabel = "3Ф",
                            installedPowerW = g.installedPowerW,
                            installedCurrentA = CircuitLoadCalculator.calculate(g.devices).installedCurrentA,
                            calculatedCurrentA = g.nominalCurrent,
                            manualNotes = g.manualDeviationCodes.map(::manualDeviationNote),
                            switchLabel = GroupMetaFormatter.buildSwitchLabel(g),
                            rcdLabel = g.fullRcdLabel(),
                            cableLabel = cableCalculations[g.groupId]?.reportCableLabel()
                                ?: GroupMetaFormatter.buildCableLabel(g),
                            devices = g.devices
                                .sortedBy { deviceKey(it) }
                                .map { d ->
                                    val normalized = PowerCurrentNormalizer.ensurePAndI(
                                        powerW = d.power.takeIf { it > 0 },
                                        currentA = d.calculateCurrent().takeIf { it > 0.0 },
                                        voltage = d.voltage,
                                        powerFactor = d.powerFactor
                                    )
                                    ReportDevice(
                                        name = d.name,
                                        powerW = normalized.powerW,
                                        currentA = normalized.currentA
                                    )
                                }
                        )
                    }
            )
        )
    } else {
        emptyList()
    }

    val phases = singlePhaseSections + threePhaseSection

    return meta to phases
}

private fun CableLineCalculation.reportCableLabel(): String = buildString {
    append(cable.compactLabel)
    input.lengthM?.let { append(" · ${formatCableNumber(it)} м") }
    voltageDropPercent?.let { append(" · ΔU ${formatCableNumber(it)}%") }
    when (status) {
        ru.mugalimov.volthome.domain.model.cable.CableCalculationStatus.PRELIMINARY ->
            append(" · предварительно")
        ru.mugalimov.volthome.domain.model.cable.CableCalculationStatus.WARNING ->
            append(" · с предупреждением")
        ru.mugalimov.volthome.domain.model.cable.CableCalculationStatus.FAILED ->
            append(" · проверка не пройдена")
        ru.mugalimov.volthome.domain.model.cable.CableCalculationStatus.PASSED -> Unit
    }
}

private fun formatCableNumber(value: Double): String =
    String.format(Locale("ru", "RU"), "%.2f", value).trimEnd('0').trimEnd(',')

private fun CircuitGroup.fullRcdLabel(): String? {
    if (!rcdRequired) return null
    val spec = rcdSpec
    if (spec == null) return "УЗО $rcdCurrent мА"

    return buildString {
        append(if (spec.kind.name == "RCBO") "АВДТ" else "УЗО")
        spec.ratedCurrentA?.let { append(" $it А") }
        append(" / ${spec.leakageCurrentMa} мА")
        append(" · тип ${spec.type.name}")
        append(" · ${spec.poles}P")
        if (spec.selectivity.name != "NONE") append(" · ${spec.selectivity.name}")
    }
}

private fun manualDeviationNote(code: String): String = when (code) {
    "MIXED_DEVICE_TYPES" ->
        "Ручное решение: в одной группе объединены устройства разных назначений."
    else ->
        "Ручное решение: $code"
}

private fun DeviceType.toReportPurpose(): String {
    return when (this) {
        DeviceType.LIGHTING -> "Освещение"
        DeviceType.SOCKET -> "Розеточная линия"
        DeviceType.HEAVY_DUTY -> "Выделенная линия"
        DeviceType.AIR_CONDITIONER -> "Кондиционер"
        DeviceType.ELECTRIC_STOVE -> "Электроплита"
        DeviceType.OVEN -> "Духовой шкаф"
        DeviceType.WASHING_MACHINE -> "Стиральная машина"
        DeviceType.DISHWASHER -> "Посудомоечная машина"
        DeviceType.WATER_HEATER -> "Водонагреватель"
        DeviceType.OTHER -> "Прочая нагрузка"
    }
}

// =========================
// PDF snapshot model
// =========================

data class PdfReportSnapshot(
    val meta: ReportMeta,
    val phases: List<ReportPhase>,
    val installedPowerW: Double,
    val calculatedPowerW: Double
)
