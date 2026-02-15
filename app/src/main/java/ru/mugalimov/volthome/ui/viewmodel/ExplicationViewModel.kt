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
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction
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

    private val _selectedDeviceBreakdown = MutableStateFlow<DeviceCalcBreakdown?>(null)
    val selectedDeviceBreakdown: StateFlow<DeviceCalcBreakdown?> = _selectedDeviceBreakdown.asStateFlow()

    // =========================
    // Unassigned devices (manual)
    // =========================

    private val _unassignedDevices = MutableStateFlow<List<Device>>(emptyList())
    val unassignedDevices: StateFlow<List<Device>> = _unassignedDevices.asStateFlow()

    // версия запроса, чтобы старые результаты не перезатирали новые (гонки при быстрых изменениях)
    private val _unassignedRequestVersion = MutableStateFlow(0)

    // =========================
    // BottomSheet payload
    // =========================

    private val _infoSheetPayload = MutableStateFlow<InfoSheetPayload?>(null)
    val infoSheetPayload: StateFlow<InfoSheetPayload?> = _infoSheetPayload.asStateFlow()

    // =========================
    // Manual session (scoped by activeProjectId)
    // =========================

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

    companion object {
        // Виртуальный fromGroupId для устройств из "Нераспределённых"
        const val FROM_UNASSIGNED: Long = -1L
    }

    // =========================
    // Drag state (manual) — Коммит 1
    // =========================

    /**
     * Цель перетаскивания (куда хотим "дропнуть").
     * В этом коммите это только состояние (UI пока не использует).
     */
    sealed class DragTarget {
        data class Group(val groupId: Long) : DragTarget()
        data object Unassigned : DragTarget()
        data object NewGroup : DragTarget()
    }

    /**
     * Полное состояние drag для manual-переноса устройств на Экспликации.
     * ВАЖНО: в этом коммите это контракт для следующих коммитов, UI пока не использует.
     */
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

    // Диалог показываем через отдельный state (StateFlow надёжнее для Compose)
    private val _showMixedWarningDialog = MutableStateFlow(false)
    val showMixedWarningDialog: StateFlow<Boolean> = _showMixedWarningDialog.asStateFlow()

    /**
     * Pending-перенос для MIXED предупреждения.
     *
     * ВАЖНО:
     * - Нельзя полагаться на _moveDeviceUi в confirm: UI уже успевает обнулить этот стейт.
     * - Поэтому храним ВСЁ, что нужно для выполнения операции после Confirm.
     */
    private data class PendingMixedMove(
        val deviceId: Long,
        val fromGroupId: Long,
        val targetGroupId: Long
    )

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

                    if (manualDraftResetNotifier.consumeResetIfNeeded(projectId, hasActiveSession = hasSession)) {
                        _events.value = UiEvent.ShowSnackbar("Черновик ручного режима был сброшен")
                    }
                }
        }

        viewModelScope.launch(ioDispatcher) {
            manualSession.collect { s ->
                if (s == null) {
                    Log.d(TAG_SESS, "manualSession=null")
                } else {
                    Log.d(TAG_SESS,
                        "manualSession pid=${s.projectId} active=${s.manualModeActive} ver=${s.version} " +
                                "groups=${s.draftState.groups.size} unassigned=${s.draftState.unassignedDeviceIds.size}"
                    )
                    Log.d(TAG_SESS, "draft.groups=" + s.draftState.groups.joinToString { "${it.groupId}#${it.groupNumber}(devs=${it.deviceIds.size})" })
                }
            }
        }
    }

    // =========================
    // Unassigned: refresh/clear
    // =========================

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
                    // Если когда-то будут id > Int.MAX_VALUE — надо менять репозиторий.
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

    /**
     * В UI может быть кнопка "Авто-распределить" для нераспределённых.
     * В этом VM делаем сразу применение action к draft (без внешнего обработчика).
     */
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

    /** Клик по PDF (Free + PRO). */
    fun onExportPdfClick() {
        _events.value = UiEvent.ExportPdfRequested
    }

    /** Экспортные действия (save/share/брендинг и т.п.) = только PRO. */
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

    /**
     * Старт drag-переноса. UI в следующих коммитах будет вызывать это после long-press.
     * В этом коммите только фиксируем состояние — без сайд-эффектов.
     */
    fun startDrag(
        deviceId: Long,
        fromGroupId: Long,
        itemStartRoot: Offset,
        pointerStartRoot: Offset
    ) {
        Log.d(TAG_DND, "startDrag deviceId=$deviceId from=$fromGroupId item=$itemStartRoot pointer=$pointerStartRoot")
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

    /**
     * UI сообщает активную цель (hover) на основании pointerRoot и bounds целей.
     * Никаких сайд-эффектов — чисто состояние для подсветки/решения drop.
     */
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

    /**
     * Обновление позиции пальца во время drag.
     * В этом коммите считаем ghostPositionRoot прямо здесь, чтобы UI мог просто рисовать.
     */
    fun updateDrag(pointerRoot: Offset) {
        val s = _dragState.value
        if (!s.isActive) {
            Log.w(TAG_DND, "updateDrag ignored: not active")
            return
        }
        Log.v(TAG_DND, "updateDrag pointer=$pointerRoot startPointer=${s.pointerStartRoot} startItem=${s.itemStartRoot}")

        val startPointer = s.pointerStartRoot ?: return
        val startItem = s.itemStartRoot ?: return

        val delta = pointerRoot - startPointer
        val ghostPos = startItem + delta

        _dragState.value = s.copy(
            pointerCurrentRoot = pointerRoot,
            ghostPositionRoot = ghostPos
        )
    }

    /**
     * Отмена drag — сбрасываем состояние.
     * В этом коммите не трогаем _moveDeviceUi, чтобы не менять текущее поведение UI.
     */
    fun cancelDrag() {
        _dragState.value = DragState()
    }

    /**
     * Drop в группу: пока только фиксируем target и завершаем drag.
     * Реальный перенос (manualRepo.apply) подключим в коммите с drop-логикой (позже).
     */
    fun dropToGroup(targetGroupId: Long) {
        Log.d("DRAG_DROP", "dropToGroup targetGroupId=$targetGroupId dragState=${_dragState.value}")

        val s = _dragState.value
        Log.d(TAG_DND, "dropToGroup target=$targetGroupId state=$s")
        val deviceId = s.draggingDeviceId ?: return
        val fromGroupId = s.fromGroupId ?: return

        // ✅ Чтобы переиспользовать текущую логику переносов (включая MIXED warning),
        // делаем вид, что перенос инициирован через панель целей.
        _moveDeviceUi.value = MoveDeviceUi(deviceId = deviceId, fromGroupId = fromGroupId)

        // Реальный перенос
        onMoveDeviceTargetGroupSelected(deviceId = deviceId, targetGroupId = targetGroupId)

        // Завершаем drag
        _dragState.value = DragState()
    }

    /**
     * Drop в "Нераспределённые": контракт под следующий коммит.
     */
    fun dropToUnassigned() {
        val s = _dragState.value
        val deviceId = s.draggingDeviceId ?: return
        val fromGroupId = s.fromGroupId ?: return

        _moveDeviceUi.value = MoveDeviceUi(deviceId = deviceId, fromGroupId = fromGroupId)
        onMoveDeviceToUnassignedSelected(deviceId = deviceId)

        _dragState.value = DragState()
    }

    /**
     * Drop "в никуда" = отмена. Сбрасываем и drag, и панель целей (чтобы UI был чистый).
     */
    fun dropCancel() {
        Log.d(TAG_DND, "dropCancel state=${_dragState.value}")
        _dragState.value = DragState()
        _moveDeviceUi.value = null
    }

    /**
     * Drop в "Новая группа": контракт под следующий коммит.
     */
    fun dropToNewGroup() {
        val s = _dragState.value
        val deviceId = s.draggingDeviceId ?: return
        val fromGroupId = s.fromGroupId ?: return

        _moveDeviceUi.value = MoveDeviceUi(deviceId = deviceId, fromGroupId = fromGroupId)
        onMoveDeviceToNewGroupSelected(deviceId = deviceId)

        _dragState.value = DragState()
    }

    // =========================
    // Manual Save / Cancel (Коммит 8)
    // =========================

    /**
     * Save:
     * - commit draft -> локальная БД (groups + joins)
     * - exit manual
     * - обновить uiState на закоммиченный результат (без авто-recalc)
     */
    fun onManualSaveRequested() {
        viewModelScope.launch(ioDispatcher) {
            val session = manualSession.value ?: return@launch
            if (!session.manualModeActive) return@launch

            val projectId = session.projectId

            try {
                // 1) Коммитим draft в БД строго в projectId сессии (а не "активного проекта")
                val committedGroups = commitManualDraftToLocalDbUseCase.execute(
                    CommitManualDraftToLocalDbUseCase.Params(
                        projectId = projectId,
                        draft = session.draftState
                    )
                )

                // 2) Выходим из manual
                manualRepo.exitManualMode(projectId)
                manualDraftResetNotifier.clearExpected(projectId)

                // 3) Обновляем UI сразу (НЕ делаем авто-recalc, иначе затрём ручные правки)
                applyGroupsToUiAfterExternalCommit(committedGroups)

                _events.value = UiEvent.ShowSnackbar("Изменения сохранены")
            } catch (_: Throwable) {
                _events.value = UiEvent.ShowSnackbar("Не удалось сохранить изменения")
            }
        }
    }

    /**
     * Cancel:
     * - exit manual (черновик отброшен)
     * - полный авто-пересчёт и сохранение результата в БД
     * - вернуть UI в AUTO pipeline
     */
    fun onManualCancelRequested() {
        viewModelScope.launch(ioDispatcher) {
            val session = manualSession.value
            val projectId = session?.projectId

            try {
                // 1) выходим из manual сразу
                if (projectId != null) {
                    manualRepo.exitManualMode(projectId)
                    manualDraftResetNotifier.clearExpected(projectId)
                }

                // 2) полный авто-recalc + commit в БД (строго по projectId)
                val pid = projectId ?: return@launch // если нет projectId — значит и отменять нечего
                cancelManualAndAutoRecalcUseCase.execute(
                    CancelManualAndAutoRecalcUseCase.Params(projectId = pid)
                )

                // 3) возвращаем UI в стандартный pipeline (чтобы totals/sections совпали на 100%)
                recalcAndSaveGroups()

                _events.value = UiEvent.ShowSnackbar("Ручные изменения отменены")
            } catch (_: Throwable) {
                _events.value = UiEvent.ShowSnackbar("Не удалось отменить изменения")
            }
        }
    }

    /**
     * Обновление UI после того, как группы уже сохранены "снаружи" (Save manual).
     * Мы НЕ делаем авто-пересчёт (иначе затрём ручные правки).
     */
    private fun applyGroupsToUiAfterExternalCommit(groups: List<CircuitGroup>) {
        val current = uiState.value as? GroupScreenState.Success ?: return

        val totalGroups = groups.size
        val totalCurrent = groups.sumOf { it.nominalCurrent }
        val hasGroupRcds = groups.any { it.rcdRequired }

        val incomer = IncomerSelector().select(
            IncomerSelector.Params(
                groups = groups,
                preferRcbo = false,
                hasGroupRcds = hasGroupRcds,
                voltageTypeOverride = when (phaseMode.value) {
                    PhaseMode.SINGLE -> VoltageType.AC_1PHASE
                    PhaseMode.THREE -> VoltageType.AC_3PHASE
                }
            )
        )

        val totals = calculateShieldOverviewUseCase.execute(groups)

        _uiState.value = current.copy(
            groups = groups,
            totalGroups = totalGroups,
            totalCurrent = totalCurrent,
            incomer = incomer,
            hasGroupRcds = hasGroupRcds,
            installedPowerW = totals.installedPowerW,
            calculatedPowerW = totals.calculatedPowerW,
        )
    }

    // =========================
    // Move device UI (manual)
    // =========================

    fun onDeviceLongPressed(deviceId: Long, fromGroupId: Long) {
        Log.d(TAG_MOVE, "onDeviceLongPressed deviceId=$deviceId fromGroupId=$fromGroupId")
        _moveDeviceUi.value = MoveDeviceUi(deviceId = deviceId, fromGroupId = fromGroupId)
    }

    fun dismissMoveDevice() {
        _moveDeviceUi.value = null
    }

    /**
     * Перенос устройства в выбранную группу.
     *
     * ФИКС (важно):
     * - Если тип устройства != тип группы => MIXED:
     *   * первый раз показываем блокирующий диалог,
     *   * confirm должен выполнить ИМЕННО перенос в targetGroup (а не MoveToUnassigned).
     * - Нельзя в confirm брать fromGroupId из _moveDeviceUi: UI успевает сбросить.
     *   Поэтому используем PendingMixedMove (deviceId/fromGroupId/targetGroupId).
     */
    fun onMoveDeviceTargetGroupSelected(deviceId: Long, targetGroupId: Long) {
        val fromGroupId = _moveDeviceUi.value?.fromGroupId
        Log.d(TAG_MOVE, "targetSelected deviceId=$deviceId from=$fromGroupId -> target=$targetGroupId moveUiWas=${_moveDeviceUi.value}")
        // ВАЖНО: фиксируем from ДО обнуления UI-стейта, иначе потеряем источник.
        val from = _moveDeviceUi.value?.fromGroupId
        _moveDeviceUi.value = null

        viewModelScope.launch(ioDispatcher) {
            Log.d("MOVE_DEVICE", "deviceId=$deviceId targetGroupId=$targetGroupId from=${from}")

            // ✅ Не полагаемся только на manualSession.value (WhileSubscribed может дать null).
            // Берём активную сессию напрямую из репозитория.
            val session = manualRepo.getActiveSession() ?: manualSession.value ?: return@launch
            Log.d(TAG_MOVE, "session pid=${session?.projectId} active=${session?.manualModeActive} ver=${session?.version}")

            Log.d("MOVE_DEVICE", "session=${session.projectId} active=${session.manualModeActive} draftGroups=${session.draftState.groups.size}")
            Log.d("MOVE_DEVICE", "draft ids=" + (session.draftState.groups.joinToString { "${it.groupId}#${it.groupNumber}" }))

            if (!session.manualModeActive) return@launch

            val state = session.draftState

            val devicesById = state.devices.associateBy { it.deviceId }
            val device = devicesById[deviceId] ?: return@launch
            val targetGroup = state.groups.firstOrNull { it.groupId == targetGroupId } ?: return@launch

            // Смешение = тип устройства не совпадает с типом группы
            val isMixed = device.deviceType != targetGroup.groupType

            // Защита: если UI не смог дать from — честно выходим
            val fromGroupId = from ?: run {
                _events.value = UiEvent.ShowSnackbar("Не удалось определить исходную группу")
                return@launch
            }

            if (!isMixed) {
                // ✅ обычный перенос
                try {
                    if (fromGroupId == FROM_UNASSIGNED) {
                        // перенос из unassigned -> group
                        manualRepo.apply(
                            ManualEditAction.MoveFromUnassigned(
                                deviceId = deviceId,
                                toGroupId = targetGroupId
                            )
                        )
                    } else {
                        // перенос group -> group
                        manualRepo.apply(
                            ManualEditAction.MoveDevice(
                                deviceId = deviceId,
                                fromGroupId = fromGroupId,
                                toGroupId = targetGroupId
                            )
                        )
                    }
                } catch (_: Throwable) {
                    _events.value = UiEvent.ShowSnackbar("Не удалось перенести устройство")
                }
                return@launch
            }

            // ✅ MIXED: первый раз — блокирующий диалог (если не отключили)
            if (!_mixedWarningDontShowAgain.value && !_mixedWarningBlocked.value) {
                _pendingMixedMove.value = PendingMixedMove(
                    deviceId = deviceId,
                    fromGroupId = fromGroupId,
                    targetGroupId = targetGroupId
                )
                _showMixedWarningDialog.value = true
                return@launch
            }

            Log.d(TAG_MOVE, "before: fromGroupDevices=" +
                    (state.groups.firstOrNull { it.groupId == fromGroupId }?.deviceIds?.size ?: -1) +
                    " targetDevices=" +
                    (state.groups.firstOrNull { it.groupId == targetGroupId }?.deviceIds?.size ?: -1)
            )

            // ✅ MIXED дальше: snackbar + выполняем перенос
            _events.value = UiEvent.ShowSnackbar("Группа помечена как MIXED (ручное смешение типов)")
            try {
                if (fromGroupId == FROM_UNASSIGNED) {
                    manualRepo.apply(
                        ManualEditAction.MoveFromUnassigned(
                            deviceId = deviceId,
                            toGroupId = targetGroupId
                        )
                    )
                } else {
                    manualRepo.apply(
                        ManualEditAction.MoveDevice(
                            deviceId = deviceId,
                            fromGroupId = fromGroupId,
                            toGroupId = targetGroupId
                        )
                    )
                }
            } catch (_: Throwable) {
                // не спамим вторым снеком
            }
            Log.d(TAG_MOVE, "apply action=MoveDevice/MoveFromUnassigned ...")
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
            // ✅ Не опираемся на _moveDeviceUi — это UI-шный стейт и он уже очищен.
            val session = manualRepo.getActiveSession() ?: manualSession.value ?: return@launch
            if (!session.manualModeActive) return@launch

            try {
                if (pending.fromGroupId == FROM_UNASSIGNED) {
                    // перенос из unassigned -> group (даже если MIXED)
                    manualRepo.apply(
                        ManualEditAction.MoveFromUnassigned(
                            deviceId = pending.deviceId,
                            toGroupId = pending.targetGroupId
                        )
                    )
                } else {
                    // перенос group -> group (MIXED)
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

    fun onMixedWarningDismiss() {
        _showMixedWarningDialog.value = false
        _pendingMixedMove.value = null
    }

    fun onMoveDeviceToNewGroupSelected(deviceId: Long) {
        _moveDeviceUi.value = null
        viewModelScope.launch(ioDispatcher) {
            val session = manualSession.value ?: return@launch
            if (!session.manualModeActive) return@launch
            try {
                manualRepo.apply(ManualEditAction.CreateNewGroupAndMove(deviceId = deviceId))
            } catch (_: Throwable) {
                _events.value = UiEvent.ShowSnackbar("Не удалось создать новую группу")
            }
        }
    }

    fun onMoveDeviceToUnassignedSelected(deviceId: Long) {
        val fromGroupId = _moveDeviceUi.value?.fromGroupId
        _moveDeviceUi.value = null

        viewModelScope.launch(ioDispatcher) {
            val session = manualSession.value ?: return@launch
            if (!session.manualModeActive) return@launch

            val from = fromGroupId ?: run {
                _events.value = UiEvent.ShowSnackbar("Не удалось определить исходную группу")
                return@launch
            }

            try {
                manualRepo.apply(
                    ManualEditAction.MoveToUnassigned(
                        deviceId = deviceId,
                        fromGroupId = from
                    )
                )
            } catch (_: Throwable) {
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

    // =========================
    // Recalc (auto)
    // =========================

    fun recalcAndSaveGroups() {
        viewModelScope.launch(ioDispatcher) {

            // ✅ Защита: в ручном режиме полный auto-recalc запрещён.
            // Иначе можно затереть черновик/ручные правки и/или рассинхронизировать UI.
            val session = manualSession.value
            if (session?.manualModeActive == true) {
                _events.value = UiEvent.ShowSnackbar("Сейчас включён ручной режим. Пересчёт недоступен.")
                return@launch
            }

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

                        // Важно: сохраняем результат пересчёта строго в активный проект
                        val projectId = activeProjectDs.activeProjectId.first().orEmpty()
                        if (projectId.isBlank()) {
                            _uiState.value = GroupScreenState.Error("Не выбран проект")
                            return@launch
                        }

                        saveAutoCalculatedGroupsToLocalDbUseCase.execute(
                            SaveAutoCalculatedGroupsToLocalDbUseCase.Params(
                                projectId = projectId,
                                groups = groups,
                                distributionDecisions = res.distributionDecisions
                            )
                        )

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

    // =========================
    // PDF snapshot
    // =========================

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

// =========================
// Screen state
// =========================

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

// =========================
// PDF snapshot model
// =========================

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