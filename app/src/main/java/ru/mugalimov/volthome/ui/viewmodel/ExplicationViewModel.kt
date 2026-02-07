package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.manual.ManualDeviceDraft
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
import ru.mugalimov.volthome.domain.model.report.DonutModel
import ru.mugalimov.volthome.domain.model.report.ReportDevice
import ru.mugalimov.volthome.domain.model.report.ReportGroup
import ru.mugalimov.volthome.domain.model.report.ReportMeta
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import ru.mugalimov.volthome.domain.model.report.professional.ProfessionalSections
import ru.mugalimov.volthome.domain.use_case.CalculateDeviceBreakdownUseCase
import ru.mugalimov.volthome.domain.use_case.CalculateGroupBreakdownUseCase
import ru.mugalimov.volthome.domain.use_case.CalculateShieldOverviewUseCase
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import ru.mugalimov.volthome.domain.use_case.getOrZero
import ru.mugalimov.volthome.domain.use_case.phaseCurrents
import ru.mugalimov.volthome.domain.use_case.report.BuildProfessionalSectionsUseCase
import ru.mugalimov.volthome.domain.util.PowerCurrentNormalizer
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcBlocksMapper
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcDetailsState
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetPayload
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetType
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
) : ViewModel() {

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

    private val _selectedDeviceBreakdown = MutableStateFlow<DeviceCalcBreakdown?>(null)
    val selectedDeviceBreakdown: StateFlow<DeviceCalcBreakdown?> =
        _selectedDeviceBreakdown.asStateFlow()

    // --- Unassigned devices (для блока "Нераспределённые") ---
    private val _unassignedDevices = MutableStateFlow<List<Device>>(emptyList())
    val unassignedDevices: StateFlow<List<Device>> = _unassignedDevices.asStateFlow()

    // версия запроса, чтобы старые результаты не перезатирали новые (гонки при быстрых изменениях)
    private val _unassignedRequestVersion = MutableStateFlow(0)

    // --- BottomSheet payload (единый для подсказок/расчётов) ---
    private val _infoSheetPayload = MutableStateFlow<InfoSheetPayload?>(null)
    val infoSheetPayload: StateFlow<InfoSheetPayload?> = _infoSheetPayload.asStateFlow()

    // --- Manual session ---
    val manualSession = activeProjectDs.activeProjectId
        .distinctUntilChanged()
        .filterNotNull()
        .flatMapLatest { projectId -> manualRepo.observeSession(projectId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class MoveDeviceUi(
        val deviceId: Long,
        val fromGroupId: Long
    )

    private val _moveDeviceUi = MutableStateFlow<MoveDeviceUi?>(null)
    val moveDeviceUi: StateFlow<MoveDeviceUi?> = _moveDeviceUi.asStateFlow()

    // --- UI events (one-shot) ---
    sealed class UiEvent {
        /**
         * Запрос на PDF (Free + PRO).
         * Имя оставляем ради совместимости с текущим UI.
         */
        data object ExportPdfRequested : UiEvent()

        /**
         * Запрос на экспортные действия (save/share/брендинг и т.п.) — только PRO.
         */
        data object PdfExportActionsRequested : UiEvent()

        /**
         * Нажатие на кнопку "Распределить автоматически…" в блоке нераспределённых.
         * Реализация auto-assign — в следующем коммите, тут только событие.
         */
        data object AutoAssignUnassignedRequested : UiEvent()
    }

    private val _events = MutableStateFlow<UiEvent?>(null)
    val events: StateFlow<UiEvent?> = _events.asStateFlow()

    init {
        // ВАЖНО: здесь НЕ дергаем recalcAndSaveGroups(), чтобы не получить двойной пересчёт,
        // если экран сам вызывает recalcAndSaveGroups() в LaunchedEffect(Unit).
        viewModelScope.launch(ioDispatcher) {
            preferencesRepository.phaseMode.collect { mode ->
                _phaseMode.value = mode
            }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    // ---- Unassigned: refresh/clear ----

    /**
     * Обновляет список Device для блока "Нераспределённые".
     * Защищено от гонок: старые результаты не перетрут новые.
     */
    fun refreshUnassignedDevices(unassignedIds: Set<Long>) {
        val version = _unassignedRequestVersion.value + 1
        _unassignedRequestVersion.value = version

        viewModelScope.launch(ioDispatcher) {
            if (unassignedIds.isEmpty()) {
                if (_unassignedRequestVersion.value == version) {
                    _unassignedDevices.value = emptyList()
                }
                return@launch
            }

            val devices = buildList {
                for (id in unassignedIds) {
                    // Если у тебя когда-то будут id > Int.MAX_VALUE — надо будет менять репозиторий.
                    val d = deviceRepository.getDeviceById(id.toInt())
                    if (d != null) add(d)
                }
            }.sortedBy { it.name.trim().lowercase(Locale.ROOT) }

            if (_unassignedRequestVersion.value == version) {
                _unassignedDevices.value = devices
            }
        }
    }

    fun clearUnassignedDevices() {
        val version = _unassignedRequestVersion.value + 1
        _unassignedRequestVersion.value = version
        _unassignedDevices.value = emptyList()
    }

    fun onAutoAssignUnassignedClick() {
        _events.value = UiEvent.AutoAssignUnassignedRequested
    }

    // ---- PDF ----

    /**
     * Клик по PDF (Free + PRO).
     */
    fun onExportPdfClick() {
        _events.value = UiEvent.ExportPdfRequested
    }

    /**
     * Экспортные действия (save/share/брендинг и т.п.) = только PRO.
     */
    fun onPdfExportActionsClick() {
        val plan = userPlanRepository.planFlow.value
        if (!plan.capabilities.pdfExport) {
            paywallBus.request(ProFeature.PRO_REPORT)
            return
        }
        _events.value = UiEvent.PdfExportActionsRequested
    }

    // ---- Manual mode entry ----

    fun onEnterManualModeClick() {
        val plan = userPlanRepository.planFlow.value
        if (!plan.capabilities.phaseDragAndDrop) {
            paywallBus.request(ProFeature.PHASE_DND_TEASER)
            return
        }

        viewModelScope.launch(ioDispatcher) {
            val projectId = activeProjectDs.activeProjectId.first().orEmpty()
            val s = uiState.value as? GroupScreenState.Success ?: return@launch
            if (projectId.isBlank()) return@launch

            val base = buildBaseEditState(projectId = projectId, groups = s.groups)
            manualRepo.enterManualMode(projectId = projectId, baseState = base)
        }
    }

    private fun buildBaseEditState(projectId: String, groups: List<CircuitGroup>): ProjectEditState {
        val deviceInstances = groups
            .flatMap { it.devices }
            .distinctBy { it.id }

        val manualDevices = deviceInstances.map { d ->
            ManualDeviceDraft(
                deviceId = d.id,
                roomId = d.roomId ?: 0L,
                deviceType = d.deviceType,
                powerW = d.power,
                voltageType = d.voltage.type,
                demandRatio = d.demandRatio,
                powerFactor = d.powerFactor,
                hasMotor = d.hasMotor,
                requiresDedicatedCircuit = d.requiresDedicatedCircuit
            )
        }

        val manualGroups = groups.map { g ->
            ManualGroupDraft(
                groupId = g.groupId,
                groupNumber = g.groupNumber,
                roomId = g.roomId ?: 0L,
                roomName = g.roomName.orEmpty(),
                groupType = g.groupType,
                phase = g.phase ?: Phase.A,
                deviceIds = g.devices.map { it.id },
                nominalCurrent = g.nominalCurrent,
                circuitBreaker = g.circuitBreaker,
                cableSection = g.cableSection,
                breakerType = g.breakerType,
                rcdRequired = g.rcdRequired,
                rcdCurrent = g.rcdCurrent
            )
        }

        val nextNum = (groups.maxOfOrNull { it.groupNumber } ?: 0) + 1

        return ProjectEditState(
            projectId = projectId,
            groups = manualGroups,
            devices = manualDevices,
            unassignedDeviceIds = emptySet(),
            nextGroupNumber = nextNum
        )
    }

    // ---- Move device UI ----

    fun onDeviceLongPressed(deviceId: Long, fromGroupId: Long) {
        _moveDeviceUi.value = MoveDeviceUi(deviceId = deviceId, fromGroupId = fromGroupId)
    }

    fun dismissMoveDevice() {
        _moveDeviceUi.value = null
    }

    fun onMoveDeviceTargetGroupSelected(deviceId: Long, targetGroupId: Long) {
        // Коммит 7/8: доменная операция + пересчёт линии
        _moveDeviceUi.value = null
    }

    fun onMoveDeviceToNewGroupSelected(deviceId: Long) {
        // Коммит 6: создание новой группы + tie-break
        _moveDeviceUi.value = null
    }

    fun onMoveDeviceToUnassignedSelected(deviceId: Long) {
        // Коммит 5: контейнер "Нераспределённые"
        // В ЭТОМ КОММИТЕ - только UI/hook. Реальная операция должна менять draftState.unassignedDeviceIds.
        _moveDeviceUi.value = null
    }

    // ---- BottomSheet ----

    fun openInfoSheet(payload: InfoSheetPayload) {
        _infoSheetPayload.value = payload
    }

    fun closeInfoSheet() {
        _infoSheetPayload.value = null
    }

    // ---- Group/Shield clicks ----

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

    /** ВАЖНО: берём ИНСТАНС устройства по id из репозитория, без дефолтов. */
    fun onDeviceClick(deviceId: Long) {
        viewModelScope.launch(ioDispatcher) {
            _selectedDeviceBreakdown.value = null

            val dev = deviceRepository.getDeviceById(deviceId.toInt())
            _selectedDevice.value = dev

            val plan = userPlanRepository.planFlow.value
            if (plan.capabilities.professionalReportSections) {
                _selectedDeviceBreakdown.value = dev?.let { calculateDeviceBreakdownUseCase.execute(it) }
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

    // ---- Recalc ----

    fun recalcAndSaveGroups() {
        viewModelScope.launch(ioDispatcher) {
            _isRecalculating.value = true
            _uiState.value = GroupScreenState.Loading

            try {
                val mode = preferencesRepository.phaseMode.first()

                val calc = groupCalculatorFactory.create()
                when (val res = calc.calculateGroups(mode)) {
                    is GroupingResult.Error -> {
                        _uiState.value = GroupScreenState.Error(res.message)
                    }

                    is GroupingResult.Success -> {
                        val groups = res.system.groups

                        repo.replaceAllGroupsTransactional(groups)
                        repo.setLastDistributionDecisions(res.distributionDecisions)

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

                        val plan = userPlanRepository.planFlow.value
                        val isProReport = plan.capabilities.professionalReportSections

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

                        // фиксируем дату в state, чтобы превью/экспорт в рамках одного пересчёта были стабильнее
                        val reportDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                            .format(System.currentTimeMillis())

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
                                        distributionDecisions = res.distributionDecisions,
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
                }
            } catch (t: Throwable) {
                _uiState.value = GroupScreenState.Error(t.message ?: "Неизвестная ошибка")
            } finally {
                _isRecalculating.value = false
            }
        }
    }

    // ---- PDF snapshot ----

    /**
     * Единый слепок для PDF:
     * - KPI (installed/calculated) строго из uiState
     * - фазы/группы/устройства — из детерминированной сборки
     */
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
}

sealed class GroupScreenState {
    data object Loading : GroupScreenState()

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

private fun buildWarningsFromGroups(groups: List<CircuitGroup>): List<CalcWarning> {
    val warnings = mutableListOf<CalcWarning>()

    groups.forEach { g ->
        g.devices.forEach { d ->
            if (d.deviceType == DeviceType.LIGHTING && (d.power ?: 0) >= 1000) {
                warnings += CalcWarning(
                    severity = CalcWarning.Severity.WARNING,
                    scope = "device:${d.id}",
                    title = "Аномальная мощность освещения",
                    message = "Освещение '${d.name}': ${(d.power ?: 0)}W"
                )
            }
        }
    }

    return warnings
}

/**
 * Детерминированная сборка данных отчёта:
 * - фазы: A/B/C (или только A в SINGLE)
 * - группы: по номеру
 * - устройства: по имени (lowercase, Locale.ROOT) — чтобы PDF не “плавал”
 *
 * date передаётся извне — никаких System.currentTimeMillis() внутри.
 */
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

/**
 * Слепок данных для PDF:
 * - meta + phases (детерминированные)
 * - installed/calculated — строго из uiState
 */
data class PdfReportSnapshot(
    val meta: ReportMeta,
    val phases: List<ReportPhase>,
    val installedPowerW: Double,
    val calculatedPowerW: Double
)