package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.local.entity.GroupPhaseOverrideEntity
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.phase_load.LoadThresholds
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadMode
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadUiState
import ru.mugalimov.volthome.domain.use_case.GetPhaseLoadUiUseCase
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import ru.mugalimov.volthome.ui.paywall.PaywallBus

@HiltViewModel
class PhaseLoadViewModel @Inject constructor(
    getPhaseLoadUiUseCase: GetPhaseLoadUiUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val explicationRepository: ExplicationRepository,
    private val incomerSelector: IncomerSelector,

    private val overrideDao: GroupPhaseOverrideDao,
    private val activeProjectDs: ActiveProjectDataStore,
    private val userPlanRepository: UserPlanRepository,
    private val paywallBus: PaywallBus
) : ViewModel() {

    private val phaseLoadMode = MutableStateFlow(PhaseLoadMode.AUTO)

    // ✅ события для snackbar
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val uiState: StateFlow<PhaseLoadUiState> =
        combine(
            getPhaseLoadUiUseCase(),
            preferencesRepository.phaseMode,
            explicationRepository.observeAllGroup(),
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
    // ✅ Режимы экрана
    // =========================

    fun onEnterManualMode() {
        if (!isUserPro()) {
            paywallBus.request(ProFeature.PHASE_DND_TEASER)
            return
        }
        phaseLoadMode.value = PhaseLoadMode.MANUAL
    }

    // =========================
    // ✅ DnD API
    // =========================

    fun onGroupDragged(groupId: Long, targetPhase: Phase) {
        // DnD возможен ТОЛЬКО в MANUAL (даже у PRO)
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
            try {
                val projectId = activeProjectDs.activeProjectId.firstOrNull()
                if (projectId.isNullOrBlank()) {
                    _events.tryEmit("Не выбран проект")
                    return@launch
                }

                overrideDao.upsert(
                    GroupPhaseOverrideEntity(
                        id = 0,
                        projectId = projectId,
                        groupId = groupId,
                        phase = targetPhase,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (t: Throwable) {
                _events.tryEmit("Не удалось изменить фазу. Попробуйте ещё раз.")
            }
        }
    }

    fun onResetOverrides() {
        // В Free — объясняющая модалка (коммит 3), без “Купить PRO?”
        if (!isUserPro()) {
            paywallBus.request(ProFeature.PHASE_DND_TEASER)
            return
        }

        viewModelScope.launch {
            try {
                val projectId = activeProjectDs.activeProjectId.firstOrNull()
                if (projectId.isNullOrBlank()) {
                    _events.tryEmit("Не выбран проект")
                    return@launch
                }

                // 1) Очищаем ручные overrides
                overrideDao.deleteByProject(projectId)

                // 2) Возвращаем режим в AUTO (безопасный откат к baseline)
                phaseLoadMode.value = PhaseLoadMode.AUTO

                _events.tryEmit("Ручные изменения сброшены")
            } catch (t: Throwable) {
                _events.tryEmit("Не удалось сбросить изменения фаз.")
            }
        }
    }

    fun onDnDLockedTapped() {
        paywallBus.request(ProFeature.PHASE_DND_TEASER)
    }

    private fun isUserPro(): Boolean =
        userPlanRepository.planFlow.value.capabilities.phaseDragAndDrop
}