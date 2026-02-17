package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction
import ru.mugalimov.volthome.domain.model.phase_load.LoadThresholds
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadMode
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadUiState
import ru.mugalimov.volthome.domain.use_case.GetPhaseLoadUiUseCase
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import ru.mugalimov.volthome.domain.use_case.manual.CancelManualAndAutoRecalcUseCase
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.utilities.ManualDraftResetNotifier

@HiltViewModel
class PhaseLoadViewModel @Inject constructor(
    getPhaseLoadUiUseCase: GetPhaseLoadUiUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val explicationRepository: ExplicationRepository,
    private val incomerSelector: IncomerSelector,

    private val manualRepo: ManualEditSessionRepository,
    private val activeProjectDs: ActiveProjectDataStore,
    private val userPlanRepository: UserPlanRepository,
    private val paywallBus: PaywallBus,
    private val cancelManualAndAutoRecalcUseCase: CancelManualAndAutoRecalcUseCase,
    private val manualDraftResetNotifier: ManualDraftResetNotifier,
) : ViewModel() {

    // ✅ manual/auto режим экрана теперь зависит от факта активной manual-сессии
    private val phaseLoadMode: StateFlow<PhaseLoadMode> =
        activeProjectDs.activeProjectId
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { projectId -> manualRepo.observeSession(projectId) }
            .map { s -> if (s?.manualModeActive == true) PhaseLoadMode.MANUAL else PhaseLoadMode.AUTO }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhaseLoadMode.AUTO)

    // ✅ события для snackbar
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    // =========================
// Groups source: AUTO from DB, MANUAL from draft (ТЗ v1.1)
// =========================

    private val groupsFlow: Flow<List<ru.mugalimov.volthome.domain.model.CircuitGroup>> =
        activeProjectDs.activeProjectId
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { projectId ->
                manualRepo.observeSession(projectId).flatMapLatest { s ->
                    if (s?.manualModeActive == true) {
                        // ✅ MANUAL: строго draft, без БД
                        flowOf(
                            s.draftState.groups.map { g ->
                                ru.mugalimov.volthome.domain.model.CircuitGroup(
                                    groupId = g.groupId,
                                    groupNumber = g.groupNumber,
                                    roomName = g.roomName,
                                    roomId = g.roomId,
                                    groupType = g.groupType,
                                    devices = emptyList(),      // Коммит 0: без устройств
                                    nominalCurrent = g.nominalCurrent ?: 0.0,
                                    installedPowerW = 0,
                                    circuitBreaker = g.circuitBreaker ?: 16,
                                    cableSection = g.cableSection ?: 2.5,
                                    breakerType = g.breakerType ?: "",
                                    rcdRequired = g.rcdRequired ?: false,
                                    rcdCurrent = g.rcdCurrent ?: 30,
                                    phase = g.phase
                                )
                            }
                        )
                    } else {
                        // ✅ AUTO: из БД
                        explicationRepository.observeAllGroup()
                    }
                }
            }

    val uiState: StateFlow<PhaseLoadUiState> =
        combine(
            getPhaseLoadUiUseCase(),                     // ⚠️ пока остаётся как есть (Коммит 5 будет переводить data на draft)
            preferencesRepository.phaseMode,
            groupsFlow,                                  // ✅ вот тут теперь строгое разделение
            explicationRepository.observeDistributionDecisions(),
            phaseLoadMode
        ) { items, mode, groups, decisions, phaseLoadMode ->

            val hasGroupRcds = groups.any { it.rcdRequired }
            val incomer = incomerSelector.select(
                IncomerSelector.Params(
                    groups = groups,
                    preferRcbo = false,
                    hasGroupRcds = hasGroupRcds
                )
            )

            PhaseLoadUiState(
                data = items,
                mode = mode,
                phaseLoadMode = phaseLoadMode,
                incomer = incomer,
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

    // =========================
    // ✅ DnD API
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
            val session = manualRepo.getActiveSession()
            if (session?.manualModeActive != true) {
                _events.tryEmit("Ручной режим не активен")
                return@launch
            }

            try {
                manualRepo.apply(
                    ManualEditAction.SetGroupPhase(
                        groupId = groupId,
                        phase = targetPhase
                    )
                )
            } catch (t: Throwable) {
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
            val session = manualRepo.getActiveSession()
            if (session?.manualModeActive != true) {
                _events.tryEmit("Ручной режим не активен")
                return@launch
            }

            try {
                // 1) выходим из manual сразу (черновик отброшен)
                manualRepo.exitManualMode(session.projectId)
                manualDraftResetNotifier.clearExpected(session.projectId)

                // 2) полный авто-recalc + commit в БД (строго по projectId)
                cancelManualAndAutoRecalcUseCase.execute(
                    CancelManualAndAutoRecalcUseCase.Params(projectId = session.projectId)
                )

                _events.tryEmit("Ручные изменения сброшены, вернулись в авто-режим")
            } catch (t: Throwable) {
                _events.tryEmit("Не удалось сбросить ручные изменения")
            }
        }
    }

    fun onDnDLockedTapped() {
        paywallBus.request(ProFeature.PHASE_DND_TEASER)
    }

    private fun isUserPro(): Boolean =
        userPlanRepository.planFlow.value.capabilities.phaseDragAndDrop
}