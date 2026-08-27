package ru.mugalimov.volthome.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.data.repository.observeResolved
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.CalculationAlgorithm
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction
import ru.mugalimov.volthome.domain.model.phase_load.LoadThresholds
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadMode
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadUiState
import ru.mugalimov.volthome.domain.use_case.GetPhaseLoadUiUseCase
import ru.mugalimov.volthome.domain.use_case.CircuitLoadCalculator
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import ru.mugalimov.volthome.domain.use_case.ObserveProjectCoverageUseCase
import ru.mugalimov.volthome.domain.model.ProjectCoverage
import ru.mugalimov.volthome.domain.use_case.manual.ResetManualOverridesAndAutoRecalcUseCase
import ru.mugalimov.volthome.domain.use_case.phase_load.PhaseLoadItemsBuilder
import ru.mugalimov.volthome.ui.onboarding.model.LoadsOnboardingFacts
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.utilities.ManualDraftResetNotifier

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PhaseLoadViewModel @Inject constructor(
    private val getPhaseLoadUiUseCase: GetPhaseLoadUiUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val projectSetupRepository: ProjectSetupRepository,
    private val explicationRepository: ExplicationRepository,
    private val deviceRepository: DeviceRepository,
    private val incomerSelector: IncomerSelector,
    private val manualRepo: ManualEditSessionRepository,
    private val activeProjectDs: ActiveProjectDataStore,
    private val userPlanRepository: UserPlanRepository,
    private val paywallBus: PaywallBus,
    private val manualDraftResetNotifier: ManualDraftResetNotifier,
    private val resetManualOverridesAndAutoRecalcUseCase: ResetManualOverridesAndAutoRecalcUseCase,
    observeProjectCoverageUseCase: ObserveProjectCoverageUseCase,
) : ViewModel() {

    val projectCoverage: StateFlow<ProjectCoverage> = observeProjectCoverageUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectCoverage.Empty)

    private data class ElectricalContext(
        val phaseMode: PhaseMode,
        val availablePowerKw: Double?,
        val unassignedDeviceCount: Int
    )

    /** Настройки конкретного проекта имеют приоритет над глобальным legacy-параметром. */
    private val electricalContext: StateFlow<ElectricalContext> =
        activeProjectDs.activeProjectId
            .flatMapLatest { projectId ->
                val pid = projectId.orEmpty().trim()
                if (pid.isBlank()) {
                    preferencesRepository.phaseMode.map { fallbackMode ->
                        ElectricalContext(fallbackMode, null, 0)
                    }
                } else {
                    val legacyFallbackMode = preferencesRepository.phaseMode.first()
                    combine(
                        projectSetupRepository.observeResolved(pid, legacyFallbackMode),
                        projectCoverage
                    ) { setup, coverage ->
                        ElectricalContext(
                            phaseMode = setup.phaseMode,
                            availablePowerKw = setup.inputPowerKw,
                            unassignedDeviceCount = coverage.unassignedDevices.size
                        )
                    }
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ElectricalContext(PhaseMode.THREE, null, 0)
            )

    // =========================
    // Mode: AUTO/MANUAL
    // =========================

    /**
     * ✅ Режим экрана "Нагрузки" зависит строго от факта активной manual-сессии.
     */
    private val phaseLoadMode: StateFlow<PhaseLoadMode> =
        activeProjectDs.activeProjectId
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { projectId -> manualRepo.observeSession(projectId) }
            .map { s -> if (s?.manualModeActive == true) PhaseLoadMode.MANUAL else PhaseLoadMode.AUTO }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhaseLoadMode.AUTO)

    // =========================
    // Events
    // =========================

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    // =========================
    // Manual session (project-scoped)
    // =========================

    /**
     * ВАЖНО: тип намеренно не фиксируем, чтобы не ловить "ProjectEditSession" vs "ManualEditSession" дрейф.
     * Нам важны только поля: manualModeActive, draftState, projectId.
     */
    private val manualSession =
        activeProjectDs.activeProjectId
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { projectId -> manualRepo.observeSession(projectId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // =========================
    // MANUAL devices cache (batch-load как в ExplicationViewModel)
    // =========================

    private val _manualDevicesById = MutableStateFlow<Map<Long, Device>>(emptyMap())
    val manualDevicesById: StateFlow<Map<Long, Device>> = _manualDevicesById.asStateFlow()

    private val _manualDevicesRequestVersion = MutableStateFlow(0)

    init {
        // ✅ Единый batch-load устройств по deviceIds из draft.groups
        viewModelScope.launch {
            manualSession
                .map { s ->
                    if (s?.manualModeActive != true) return@map emptySet<Long>()
                    val draft = s.draftState
                    draft.groups.asSequence().flatMap { it.deviceIds.asSequence() }.toSet()
                }
                .distinctUntilChanged()
                .collectLatest { neededIds ->
                    if (neededIds.isEmpty()) {
                        val ver = _manualDevicesRequestVersion.value + 1
                        _manualDevicesRequestVersion.value = ver
                        _manualDevicesById.value = emptyMap()
                        Log.d("PHASE_MANUAL_DEV", "CLEAR ids=0 ver=$ver")
                        return@collectLatest
                    }

                    val ver = _manualDevicesRequestVersion.value + 1
                    _manualDevicesRequestVersion.value = ver

                    Log.d("PHASE_MANUAL_DEV", "LOAD start ids=${neededIds.size} ver=$ver")

                    val devices = deviceRepository.getDevicesByIds(neededIds.toList())
                    val map = devices.associateBy { it.id }

                    // ✅ защита от гонок: старый запрос не может перезатереть новый
                    if (_manualDevicesRequestVersion.value == ver) {
                        _manualDevicesById.value = map
                        Log.d("PHASE_MANUAL_DEV", "LOAD apply ids=${map.size} ver=$ver")
                    } else {
                        Log.w(
                            "PHASE_MANUAL_DEV",
                            "LOAD drop stale ids=${map.size} ver=$ver current=${_manualDevicesRequestVersion.value}"
                        )
                    }
                }
        }
    }

    // =========================
    // Groups source: AUTO from DB, MANUAL from draft+devicesById
    // =========================

    private val manualGroupsFlow: Flow<List<CircuitGroup>> =
        combine(manualSession, manualDevicesById) { s, devicesById ->
            if (s?.manualModeActive != true) return@combine emptyList()
            val draft = s.draftState

            draft.groups.map { g ->
                val groupDevices = g.deviceIds.mapNotNull { id -> devicesById[id] }

                // ✅ Не подменяем отсутствующий cable result "магическими" 2.5.
                val resolvedBreaker = g.circuitBreaker ?: run {
                    Log.e(
                        "PHASE_MANUAL_GROUPS",
                        "Missing circuitBreaker in manual draft groupId=${g.groupId} groupNumber=${g.groupNumber}"
                    )
                    16
                }

                val resolvedCable = g.cableSection ?: run {
                    Log.e(
                        "PHASE_MANUAL_GROUPS",
                        "Missing cableSection in manual draft groupId=${g.groupId} groupNumber=${g.groupNumber}"
                    )
                    0.0
                }

                val resolvedBreakerType = g.breakerType ?: run {
                    Log.e(
                        "PHASE_MANUAL_GROUPS",
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

                    // Линия/номиналы живут в draft и должны приходить уже после line recalc.
                    nominalCurrent = g.nominalCurrent ?: 0.0,
                    installedPowerW = CircuitLoadCalculator
                        .calculate(groupDevices)
                        .installedPowerW
                        .toInt(),

                    circuitBreaker = resolvedBreaker,
                    cableSection = resolvedCable,
                    breakerType = resolvedBreakerType,

                    // ✅ Прокидываем explanation из manual draft,
                    // чтобы MANUAL и AUTO не расходились по runtime-данным.
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
        }

    private val autoGroupsFlow: Flow<List<CircuitGroup>> =
        explicationRepository.observeAllGroup()

    /**
     * ✅ Для расчёта incomer/thresholds используем тот же источник групп:
     * - MANUAL: draft+devices
     * - AUTO: DB
     */
    private val groupsFlow: Flow<List<CircuitGroup>> =
        phaseLoadMode.flatMapLatest { mode ->
            if (mode == PhaseLoadMode.MANUAL) manualGroupsFlow else autoGroupsFlow
        }

    val groupsCount: StateFlow<Int> =
        groupsFlow
            .map { it.size }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)



    // =========================
    // Phase items source: AUTO from usecase, MANUAL from draft groups
    // =========================

    private val phaseItemsFlow =
        phaseLoadMode.flatMapLatest { mode ->
            if (mode == PhaseLoadMode.MANUAL) {
                // ✅ MANUAL: строим PhaseLoadItem из draft групп (с реальными devices)
                manualGroupsFlow.map { groups -> PhaseLoadItemsBuilder.build(groups) }
            } else {
                // ✅ AUTO: как было (overrides остаются в use case)
                getPhaseLoadUiUseCase()
            }
        }

    // =========================
    // UI state
    // =========================

    val uiState: StateFlow<PhaseLoadUiState> =
        combine(
            phaseItemsFlow,
            electricalContext,
            groupsFlow,
            explicationRepository.observeDistributionDecisions(),
            phaseLoadMode
        ) { items, context, groups, decisions, currentMode ->

            val hasGroupRcds = groups.any { it.rcdRequired }
            val incomerAssessment = incomerSelector.assess(
                IncomerSelector.Params(
                    groups = groups,
                    preferRcbo = false,
                    hasGroupRcds = hasGroupRcds,
                    availablePowerKw = context.availablePowerKw,
                    unassignedDeviceCount = context.unassignedDeviceCount,
                    voltageTypeOverride = when (context.phaseMode) {
                        PhaseMode.SINGLE -> VoltageType.AC_1PHASE
                        PhaseMode.THREE -> VoltageType.AC_3PHASE
                    }
                )
            )
            val incomer = incomerAssessment.spec

            PhaseLoadUiState(
                data = items,
                mode = context.phaseMode,
                phaseLoadMode = currentMode,
                incomer = incomer,
                incomerAssessment = incomerAssessment,
                thresholds = LoadThresholds(),
                decisions = decisions
            )
        }
            .onStart { emit(PhaseLoadUiState(isLoading = true)) }
            .catch { emit(PhaseLoadUiState(error = it)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PhaseLoadUiState(isLoading = true)
            )

    /**
     * Канонический источник фактов для Loads onboarding.
     *
     * Источник:
     * - только PhaseLoadViewModel
     * - groupsCount + uiState
     */
    val onboardingFacts: StateFlow<LoadsOnboardingFacts> =
        combine(
            groupsCount,
            uiState
        ) { count, state ->
            LoadsOnboardingFacts(
                groupsCount = count,
                phaseMode = state.mode,
                phaseLoadMode = state.phaseLoadMode,
                isLoading = state.isLoading
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LoadsOnboardingFacts()
        )

    // =========================
    // DnD API
    // =========================

    fun onGroupDragged(groupId: Long, targetPhase: Phase) {
        // DnD возможен ТОЛЬКО в MANUAL
        if (phaseLoadMode.value != PhaseLoadMode.MANUAL) return

        if (!isUserPro()) {
            paywallBus.request(ProFeature.PHASE_DND_TEASER)
            return
        }

        // ✅ Guard: 3φ группы нельзя переносить
        val isThreePhaseGroup = uiState.value.data
            .firstOrNull { it.phase == Phase.THREE_PHASE }
            ?.groups
            ?.any { it.groupId == groupId }
            ?: false

        if (isThreePhaseGroup) {
            _events.tryEmit("3-фазную нагрузку нельзя переносить между фазами")
            return
        }

        viewModelScope.launch {
            val projectId = currentProjectIdOrNull()
            if (projectId == null) {
                _events.tryEmit("Не выбран проект")
                return@launch
            }

            val session = manualRepo.getSession(projectId)
            if (session?.manualModeActive != true) {
                _events.tryEmit("Ручной режим не активен")
                return@launch
            }

            try {
                manualRepo.apply(
                    projectId = projectId,
                    action = ManualEditAction.SetGroupPhase(
                        groupId = groupId,
                        phase = targetPhase
                    )
                )
            } catch (t: IllegalArgumentException) {
                _events.tryEmit(
                    t.message?.substringAfter(": ")
                        ?: "Эту группу нельзя назначить на выбранную фазу"
                )
            } catch (_: Throwable) {
                _events.tryEmit("Не удалось изменить фазу. Попробуйте ещё раз.")
            }
        }
    }

    fun onResetOverrides() {
        if (!isUserPro()) {
            paywallBus.request(ProFeature.PHASE_DND_TEASER)
            return
        }

        viewModelScope.launch {
            val projectId = currentProjectIdOrNull()
            if (projectId == null) {
                _events.tryEmit("Не выбран проект")
                return@launch
            }

            val session = manualRepo.getSession(projectId)
            if (session?.manualModeActive != true) {
                _events.tryEmit("Ручной режим не активен")
                return@launch
            }

            try {
                // Выходим из manual строго для текущего проекта.
                manualRepo.exitManualMode(projectId)
                manualDraftResetNotifier.clearExpected(projectId)

                // После выхода выполняем полный auto-recalc тоже строго по текущему projectId.
                val phaseMode = electricalContext.value.phaseMode

                resetManualOverridesAndAutoRecalcUseCase.execute(
                    ResetManualOverridesAndAutoRecalcUseCase.Params(
                        projectId = projectId,
                        phaseMode = phaseMode
                    )
                )

                _events.tryEmit("Ручные изменения сброшены. Включён автоматический режим")
            } catch (_: Throwable) {
                _events.tryEmit("Не удалось сбросить ручные изменения")
            }
        }
    }

    fun onDnDLockedTapped() {
        paywallBus.request(ProFeature.PHASE_DND_TEASER)
    }

    private fun isUserPro(): Boolean =
        userPlanRepository.planFlow.value.capabilities.phaseDragAndDrop

    /**
     * Возвращает текущий projectId в нормализованном виде.
     */
    private suspend fun currentProjectIdOrNull(): String? =
        activeProjectDs.activeProjectId.first()
            .orEmpty()
            .trim()
            .takeIf { it.isNotBlank() }
}
