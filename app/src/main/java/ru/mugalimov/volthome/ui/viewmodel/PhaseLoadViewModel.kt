package ru.mugalimov.volthome.ui.screens.loads

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
import ru.mugalimov.volthome.domain.model.UserPlan
import ru.mugalimov.volthome.domain.model.phase_load.LoadThresholds
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

    // ✅ события для snackbar
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    // ✅ флаг PRO для UI (не единственная защита — в методах тоже есть guard)
    val isPro: StateFlow<Boolean> =
        userPlanRepository.planFlow
            .map { it.isPro }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPlan.FREE.isPro)

    val uiState: StateFlow<PhaseLoadUiState> =
        combine(
            getPhaseLoadUiUseCase(),
            preferencesRepository.phaseMode,
            explicationRepository.observeAllGroup()
        ) { items, mode, groups ->
            val data = if (mode == PhaseMode.SINGLE) {
                // ✅ FIX: в 1φ режиме показываем ТОЛЬКО Phase.A
                // Phase.THREE_PHASE полностью исключён из UI
                items.filter { it.phase == Phase.A }
            } else {
                items
            }

            val hasGroupRcds = groups.any { it.rcdRequired }
            val incomer = incomerSelector.select(
                IncomerSelector.Params(
                    groups = groups,
                    preferRcbo = false,
                    hasGroupRcds = hasGroupRcds
                )
            )

            PhaseLoadUiState(
                data = data,
                mode = mode,
                incomer = incomer,
                thresholds = LoadThresholds()
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
        if (!isUserPro()) {
            paywallBus.request(ProFeature.PHASE_DND_TEASER)
            return
        }

        // ✅ Guard: 3φ группы нельзя переносить (они не принадлежат A/B/C)
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

                overrideDao.deleteByProject(projectId)
            } catch (t: Throwable) {
                _events.tryEmit("Не удалось сбросить изменения фаз.")
            }
        }
    }

    fun onDnDLockedTapped() {
        paywallBus.request(ProFeature.PHASE_DND_TEASER)
    }

    private fun isUserPro(): Boolean = userPlanRepository.planFlow.value.isPro
}