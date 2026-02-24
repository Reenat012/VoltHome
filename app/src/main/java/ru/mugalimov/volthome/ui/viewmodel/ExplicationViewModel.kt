package ru.mugalimov.volthome.ui.viewmodel

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.formatter.GroupMetaFormatter
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcWarning
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceCalcBreakdown
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction
import ru.mugalimov.volthome.domain.model.manual.ManualEditSession
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
import ru.mugalimov.volthome.domain.model.report.DonutModel
import ru.mugalimov.volthome.domain.model.report.ReportDevice
import ru.mugalimov.volthome.domain.model.report.ReportGroup
import ru.mugalimov.volthome.domain.model.report.ReportMeta
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import ru.mugalimov.volthome.domain.model.report.professional.ProfessionalSections
import ru.mugalimov.volthome.domain.telemetry.CreateDeviceOpBus
import ru.mugalimov.volthome.domain.use_case.CalculateDeviceBreakdownUseCase
import ru.mugalimov.volthome.domain.use_case.CalculateGroupBreakdownUseCase
import ru.mugalimov.volthome.domain.use_case.CalculateShieldOverviewUseCase
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import ru.mugalimov.volthome.domain.use_case.SaveAutoCalculatedGroupsToLocalDbUseCase
import ru.mugalimov.volthome.domain.use_case.getOrZero
import ru.mugalimov.volthome.domain.use_case.phaseCurrents
import ru.mugalimov.volthome.domain.use_case.report.BuildProfessionalSectionsUseCase
import ru.mugalimov.volthome.domain.use_case.manual.CancelManualAndAutoRecalcUseCase
import ru.mugalimov.volthome.domain.use_case.manual.CommitManualDraftToLocalDbUseCase
import ru.mugalimov.volthome.domain.util.PowerCurrentNormalizer
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcBlocksMapper
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcDetailsState
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetPayload
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetType
import ru.mugalimov.volthome.ui.utilities.ManualDraftResetNotifier
import ru.mugalimov.volthome.ui.viewmodel.explication.InfoSheetPayloadFactory

@HiltViewModel
class ExplicationViewModel @Inject constructor(
    private val repo: ExplicationRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: PreferencesRepository,
    private val deviceRepository: DeviceRepository,
    private val calculateShieldOverviewUseCase: CalculateShieldOverviewUseCase,
    private val calculateDeviceBreakdownUseCase: CalculateDeviceBreakdownUseCase,
    private val calculateGroupBreakdownUseCase: CalculateGroupBreakdownUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    val userPlanRepository: UserPlanRepository,
    private val paywallBus: PaywallBus,
    private val activeProjectDs: ActiveProjectDataStore,
    private val manualRepo: ManualEditSessionRepository,

    private val commitManualDraftToLocalDbUseCase: CommitManualDraftToLocalDbUseCase,
    private val cancelManualAndAutoRecalcUseCase: CancelManualAndAutoRecalcUseCase,
    private val saveAutoCalculatedGroupsToLocalDbUseCase: SaveAutoCalculatedGroupsToLocalDbUseCase,

    // ✅ Коммит 8: kill-process UX маркер
    private val manualDraftResetNotifier: ManualDraftResetNotifier,
    private val createDeviceOpBus: CreateDeviceOpBus,
) : ViewModel() {

    private val TAG_DND = "EXP_DND"
    private val TAG_MOVE = "EXP_MOVE"
    private val TAG_SESS = "EXP_SESS"

    // =========================
    // UI state
    // =========================

    private val _uiState = MutableStateFlow<GroupScreenState>(GroupScreenState.Loading)
    val uiState: StateFlow<GroupScreenState> = _uiState.asStateFlow()

    private val _isRecalculating = MutableStateFlow(false)
    val isRecalculating: StateFlow<Boolean> = _isRecalculating.asStateFlow()

    // последний известный режим фаз (для buildReportSnapshotForPdf)
    private val _phaseMode = MutableStateFlow(PhaseMode.THREE)
    val phaseMode: StateFlow<PhaseMode> = _phaseMode.asStateFlow()

    // выбранный инстанс устройства для шита
    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

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
        combine(manualSession, manualDevicesById) { s: ManualEditSession?, devicesById: Map<Long, Device> ->
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
        combine(manualSession, manualDevicesById) { s: ManualEditSession?, devicesById: Map<Long, Device> ->
            val draft = s?.draftState ?: return@combine emptyList()
            if (s.manualModeActive != true) return@combine emptyList()

            draft.groups.map { g ->
                val groupDevices = g.deviceIds.mapNotNull { id -> devicesById[id] }

                CircuitGroup(
                    groupId = g.groupId,
                    groupNumber = g.groupNumber,
                    roomName = g.roomName,
                    roomId = g.roomId,
                    groupType = g.groupType,
                    devices = groupDevices,
                    nominalCurrent = g.nominalCurrent ?: 0.0,
                    installedPowerW = groupDevices.sumOf { it.power },
                    circuitBreaker = g.circuitBreaker ?: 16,
                    cableSection = g.cableSection ?: 2.5,
                    breakerType = g.breakerType ?: "",
                    rcdRequired = g.rcdRequired ?: false,
                    rcdCurrent = g.rcdCurrent ?: 30,
                    phase = g.phase
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ✅ Делаем projectId доступным как StateFlow (чтобы не блокировать first() внутри collect)
    private val activeProjectIdState: StateFlow<String?> =
        activeProjectDs.activeProjectId
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    sealed class UiEvent {
        data object ExportPdfRequested : UiEvent()
        data object PdfExportActionsRequested : UiEvent()
        data object AutoAssignUnassignedRequested : UiEvent()
        data class ShowSnackbar(val message: String) : UiEvent()
    }

    fun consumeEvent() {
        _events.value = null
    }

    // =========================
    // MIXED_MANUAL warning state (сессионно)
    // =========================

    private val _mixedWarningBlocked = MutableStateFlow(false)
    val mixedWarningBlocked: StateFlow<Boolean> = _mixedWarningBlocked.asStateFlow()

    private val _mixedWarningDontShowAgain = MutableStateFlow(false)

    private val _showMixedWarningDialog = MutableStateFlow(false)
    val showMixedWarningDialog: StateFlow<Boolean> = _showMixedWarningDialog.asStateFlow()

    private data class PendingMixedMove(
        val deviceId: Long,
        val fromGroupId: Long,
        val targetGroupId: Long
    )

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private val _pendingMixedMove = MutableStateFlow<PendingMixedMove?>(null)

    // =========================
    // Init
    // =========================

    init {
        // 1) Следим за phaseMode из preferences
        viewModelScope.launch(ioDispatcher) {
            preferencesRepository.phaseMode.collect { mode ->
                _phaseMode.value = mode
            }
        }

        // 2) Kill-process UX:
        // Если manual ожидался (маркер стоит), но сессии нет => процесс был убит => показываем уведомление.
        viewModelScope.launch(ioDispatcher) {
            activeProjectDs.activeProjectId
                .filterNotNull()
                .distinctUntilChanged()
                .collect { projectId ->
                    val s = manualRepo.getActiveSession()
                    val hasSession = (s?.projectId == projectId) && (s.manualModeActive)

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
                    if (s?.manualModeActive != true) return@map emptySet<Long>()

                    val draft = s.draftState
                    val assigned = draft.groups.asSequence().flatMap { it.deviceIds.asSequence() }
                    val unassigned = draft.unassignedDeviceIds.asSequence()

                    (assigned + unassigned).toSet()
                }
                .distinctUntilChanged() // если набор id не изменился — не дёргаем БД повторно
                .collectLatest { neededIds ->
                    // если manual выключен или id пустые — чистим кэш
                    if (neededIds.isEmpty()) {
                        val version = _manualDevicesRequestVersion.value + 1
                        _manualDevicesRequestVersion.value = version
                        _manualDevicesById.value = emptyMap()
                        Log.d("MANUAL_DEVICES", "CLEAR neededIds=0 ver=$version")
                        return@collectLatest
                    }

                    val version = _manualDevicesRequestVersion.value + 1
                    _manualDevicesRequestVersion.value = version

                    Log.d("MANUAL_DEVICES", "LOAD start ids=${neededIds.size} ver=$version")

                    val devices = deviceRepository.getDevicesByIds(neededIds.toList())
                    val returnedIds = devices.map { it.id }.toSet()
                    val missing = neededIds - returnedIds

                    Log.d(
                        "MANUAL_DEVICES",
                        "LOAD result returned=${returnedIds.size} missing=${missing.size} missingIds=${missing.take(20)}"
                    )

                    val map = devices.associateBy { it.id }

                    // ✅ защита от гонок: старый запрос не имеет права перезатереть новый
                    if (_manualDevicesRequestVersion.value == version) {
                        _manualDevicesById.value = map
                        Log.d("MANUAL_DEVICES", "LOAD apply ids=${map.size} ver=$version")
                    } else {
                        Log.w(
                            "MANUAL_DEVICES",
                            "LOAD drop stale ids=${map.size} ver=$version current=${_manualDevicesRequestVersion.value}"
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

            val withManualGroupsFlow: Flow<DbWithSessionAndManualGroups> =
                withSessionFlow.combine(manualDisplayGroups) { base: DbWithSession, manualGroups: List<CircuitGroup> ->
                    DbWithSessionAndManualGroups(
                        base = base,
                        manualGroups = manualGroups
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
                                phaseMode,
                                decisionsFlow,
                                manualSession,
                                manualDisplayGroups
                            ) { groups, mode, decisions, session, manualGroups ->
                                Snapshot(
                                    groups = groups,
                                    mode = mode,
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
                            decisions = decisions
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
                        decisions = decisions
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
        decisions: List<DistributionDecision>
    ) {
        val totalGroups = groups.size
        val totalCurrent = groups.sumOf { it.nominalCurrent }
        val hasGroupRcds = groups.any { it.rcdRequired }

        val incomer = IncomerSelector().select(
            IncomerSelector.Params(
                groups = groups,
                preferRcbo = false,
                hasGroupRcds = hasGroupRcds,
                voltageTypeOverride = when (mode) {
                    PhaseMode.SINGLE -> VoltageType.AC_1PHASE
                    PhaseMode.THREE -> VoltageType.AC_3PHASE
                }
            )
        )

        val totals = calculateShieldOverviewUseCase.execute(groups)
        val installedPower = totals.installedPowerW
        val calculatedPower = totals.calculatedPowerW

        val calcWarningsFromGroups = if (isProReport) {
            buildWarningsFromGroups(groups)
        } else emptyList()

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
            totalGroups = totalGroups,
            mode = mode,
            date = reportDate
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

    fun refreshUnassignedDevices(unassignedIds: Set<Long>) {
        Log.d(
            "MANUAL_DEVICES",
            "refreshUnassignedDevices ignored (handled by manualDevicesById) ids=${unassignedIds.size}"
        )
    }

    fun clearUnassignedDevices() {
        Log.d("MANUAL_DEVICES", "clearUnassignedDevices ignored (handled by manualDevicesById)")
    }

    fun onAutoAssignUnassignedClick() {
        viewModelScope.launch(ioDispatcher) {
            val session = manualSession.value
            if (session?.manualModeActive != true) return@launch
            try {
                manualRepo.apply(ManualEditAction.AutoAssignUnassigned)
                _events.value = UiEvent.ShowSnackbar("Нераспределённые устройства распределены")
            } catch (_: Throwable) {
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

    fun onCancelManualClicked() {
        viewModelScope.launch(ioDispatcher) {

            val projectId = activeProjectDs.activeProjectId.first().orEmpty()

            if (projectId.isBlank()) {
                _events.value = UiEvent.ShowSnackbar("Не выбран проект")
                return@launch
            }

            val session = manualRepo.getActiveSession() ?: manualSession.value
            val isManual = session?.manualModeActive == true && session.projectId == projectId
            if (!isManual) {
                _events.value = UiEvent.ShowSnackbar("Ручной режим не активен")
                return@launch
            }

            Log.w("MANUAL_CANCEL", "VM Cancel START pid=$projectId ver=${session?.version}")

            _isRecalculating.value = true
            _uiState.value = GroupScreenState.Loading

            try {
                when (val res = cancelManualAndAutoRecalcUseCase.execute(
                    CancelManualAndAutoRecalcUseCase.Params(projectId = projectId)
                )) {
                    is GroupingResult.Error -> {
                        Log.e("MANUAL_CANCEL", "VM Cancel FAILED pid=$projectId msg=${res.message}")
                        _events.value =
                            UiEvent.ShowSnackbar("Не удалось отменить изменения: ${res.message}")
                        return@launch
                    }

                    is GroupingResult.Success -> {
                        Log.w(
                            "MANUAL_CANCEL",
                            "VM Cancel OK pid=$projectId groups=${res.system.groups.size}"
                        )

                        repo.setLastDistributionDecisions(res.distributionDecisions)

                        manualRepo.exitManualMode(projectId)

                        _moveDeviceUi.value = null
                        _dragState.value = DragState()
                        _pendingMixedMove.value = null
                        _showMixedWarningDialog.value = false

                        _events.value = UiEvent.ShowSnackbar("Ручные изменения отменены")
                    }
                }
            } catch (t: Throwable) {
                Log.e("MANUAL_CANCEL", "VM Cancel EXCEPTION pid=$projectId", t)
                _events.value = UiEvent.ShowSnackbar("Ошибка отмены изменений")
            } finally {
                _isRecalculating.value = false
            }
        }
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

            val session = manualRepo.getActiveSession()
            if (session == null) {
                Log.e("MOVE_DEBUG", "session=null")
                _events.value = UiEvent.ShowSnackbar("Manual session = null")
                return@launch
            }

            if (!session.manualModeActive) {
                Log.e("MOVE_DEBUG", "manualModeActive=false")
                _events.value = UiEvent.ShowSnackbar("Manual mode inactive")
                return@launch
            }

            val fromGroupId = from ?: run {
                Log.e("MOVE_DEBUG", "fromGroupId=null")
                return@launch
            }

            Log.d(
                "DRAG_TRACE",
                "VM APPLY MOVE device=$deviceId from=$fromGroupId to=$targetGroupId"
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

            Log.d("DRAG_TRACE", "VM APPLY action=$action")
            manualRepo.apply(action)
        }
    }

    fun onMixedWarningConfirm(dontShowAgainInSession: Boolean) {
        _showMixedWarningDialog.value = false
        _mixedWarningBlocked.value = true
        if (dontShowAgainInSession) _mixedWarningDontShowAgain.value = true

        val pending = _pendingMixedMove.value
        _pendingMixedMove.value = null

        if (pending == null) return

        viewModelScope.launch(ioDispatcher) {
            val session = manualRepo.getActiveSession() ?: manualSession.value ?: return@launch
            if (!session.manualModeActive) return@launch

            try {
                if (pending.fromGroupId == FROM_UNASSIGNED) {
                    manualRepo.apply(
                        ManualEditAction.MoveFromUnassigned(
                            deviceId = pending.deviceId,
                            toGroupId = pending.targetGroupId
                        )
                    )
                } else {
                    manualRepo.apply(
                        ManualEditAction.MoveDevice(
                            deviceId = pending.deviceId,
                            fromGroupId = pending.fromGroupId,
                            toGroupId = pending.targetGroupId
                        )
                    )
                }
                _events.value = UiEvent.ShowSnackbar("Перенос выполнен (MIXED)")
            } catch (_: Throwable) {
                _events.value = UiEvent.ShowSnackbar("Не удалось перенести устройство")
            }
        }
    }

    fun onMoveDeviceToNewGroupSelected(deviceId: Long) {
        _moveDeviceUi.value = null

        viewModelScope.launch(ioDispatcher) {
            val session = manualRepo.getActiveSession() ?: manualSession.value
            if (session == null) {
                Log.d(
                    TAG_SESS,
                    "onMoveDeviceToNewGroupSelected: session=null (ignored) deviceId=$deviceId"
                )
                _events.value = UiEvent.ShowSnackbar("Сессия ручного режима недоступна")
                return@launch
            }

            if (!session.manualModeActive) {
                Log.w(
                    TAG_SESS,
                    "onMoveDeviceToNewGroupSelected: manualModeActive=false deviceId=$deviceId"
                )
                _events.value = UiEvent.ShowSnackbar("Ручной режим выключен")
                return@launch
            }

            Log.d(
                TAG_MOVE,
                "toNewGroup deviceId=$deviceId pid=${session.projectId} ver=${session.version}"
            )

            try {
                manualRepo.apply(ManualEditAction.CreateNewGroupAndMove(deviceId = deviceId))
            } catch (t: Throwable) {
                Log.e(TAG_MOVE, "toNewGroup failed deviceId=$deviceId", t)
                _events.value = UiEvent.ShowSnackbar("Не удалось создать новую группу")
            }
        }
    }

    fun onMoveDeviceToUnassignedSelected(deviceId: Long) {
        val fromGroupId = _moveDeviceUi.value?.fromGroupId
        _moveDeviceUi.value = null

        viewModelScope.launch(ioDispatcher) {
            val session = manualRepo.getActiveSession() ?: manualSession.value
            if (session == null) {
                Log.w(
                    TAG_SESS,
                    "onMoveDeviceToUnassignedSelected: session=null deviceId=$deviceId from=$fromGroupId"
                )
                _events.value = UiEvent.ShowSnackbar("Сессия ручного режима недоступна")
                return@launch
            }

            if (!session.manualModeActive) {
                Log.w(
                    TAG_SESS,
                    "onMoveDeviceToUnassignedSelected: manualModeActive=false deviceId=$deviceId"
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
                "toUnassigned deviceId=$deviceId from=$from pid=${session.projectId} ver=${session.version}"
            )

            try {
                manualRepo.apply(
                    ManualEditAction.MoveToUnassigned(
                        deviceId = deviceId,
                        fromGroupId = from
                    )
                )
            } catch (t: Throwable) {
                Log.e(TAG_MOVE, "toUnassigned failed deviceId=$deviceId from=$from", t)
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

    fun clearSelected() {
        _selectedDevice.value = null
        _selectedDeviceBreakdown.value = null
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
                normRefs = if (hasAccess) calculated.normRefs.map { it.toString() } else emptyList()
            )
        )
    }

    fun onIncomerFieldClick(
        field: InfoSheetPayloadFactory.IncomerField,
        incomer: IncomerSpec,
        hasGroupRcds: Boolean
    ) {
        openInfoSheet(
            InfoSheetPayloadFactory.incomerField(
                field = field,
                incomer = incomer,
                phaseMode = phaseMode.value,
                hasGroupRcds = hasGroupRcds
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

            val activeSession = manualRepo.getActiveSession() ?: manualSession.value
            val isManual = activeSession?.manualModeActive == true
            val manualPid = activeSession?.projectId

            if (isManual) {
                Log.w(
                    "AUTO_GATE",
                    "AUTO_RECALC_BLOCKED reason=EXPLICIT_RECALC_REQUEST manual=true pid=$manualPid ver=${activeSession?.version}"
                )
                _events.value =
                    UiEvent.ShowSnackbar("Сейчас включён ручной режим. Пересчёт недоступен.")
                return@launch
            }

            val projectId = activeProjectIdState.value.orEmpty()
            if (projectId.isBlank()) {
                Log.w(
                    "AUTO_GATE",
                    "AUTO_RECALC_ABORT reason=NO_ACTIVE_PROJECT (keep current uiState)"
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
        Log.w("AUTO_GATE", "AUTO_RECALC_ALLOWED reason=$reason pid=$projectId")

        _isRecalculating.value = true
        _uiState.value = GroupScreenState.Loading

        try {
            val mode = preferencesRepository.phaseMode.first()

            Log.w("AUTO_TRIGGER", "AUTO_RECALC_START reason=$reason pid=$projectId mode=$mode")

            val calc = groupCalculatorFactory.create()
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
                    saveAutoCalculatedGroupsToLocalDbUseCase.execute(
                        SaveAutoCalculatedGroupsToLocalDbUseCase.Params(
                            projectId = projectId,
                            groups = groups,
                            distributionDecisions = res.distributionDecisions
                        )
                    )

                    repo.setLastDistributionDecisions(res.distributionDecisions)

                    Log.w(
                        "AUTO_TRIGGER",
                        "AUTO_RECALC_OK pid=$projectId groups=${groups.size} (uiSuccess=$updateUiSuccess)"
                    )

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
            totalGroups = s.totalGroups,
            mode = mode,
            date = s.reportDate
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
    totalGroups: Int,
    mode: PhaseMode,
    date: String
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
                append("${mcbRating}A ${mcbCurve}, Icn ${icn}")
                rcdType?.let { append(", RCD $it ${rcdSensitivityMa ?: 30}mA") }
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

    val phases = phaseKeys(mode).map { phase ->
        val phaseGroups = groupedByPhase[phase].orEmpty()

        ReportPhase(
            name = "Фаза ${phase.name}",
            groups = phaseGroups
                .sortedBy { it.groupNumber }
                .map { g ->
                    ReportGroup(
                        title = "Группа #${g.groupNumber} — ${g.roomName}",
                        switchLabel = GroupMetaFormatter.buildSwitchLabel(g),
                        cableLabel = GroupMetaFormatter.buildCableLabel(g),
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

    return meta to phases
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