package ru.mugalimov.volthome.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.use_case.manual.ProjectBaseStateBuilder
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.utilities.ManualDraftResetNotifier

/**
 * Глобальная VM для AppBar:
 * - кнопка "Ручной режим" должна работать на любой вкладке
 * - состояние manual должно быть единым на проект
 *
 * ВАЖНО:
 * - Не используем ExplicationViewModel здесь, чтобы не создавать вторую VM в другом скоупе.
 * - Вся логика входа в manual находится тут (paywall + baseState + маркер kill-process UX).
 */
@HiltViewModel
class ManualModeAppBarViewModel @Inject constructor(
    private val activeProjectDs: ActiveProjectDataStore,
    private val manualRepo: ManualEditSessionRepository,
    private val userPlanRepository: UserPlanRepository,
    private val paywallBus: PaywallBus,
    private val projectBaseStateBuilder: ProjectBaseStateBuilder,
    private val manualDraftResetNotifier: ManualDraftResetNotifier,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    /**
     * UI-одноразовые сообщения.
     * Сейчас не обязательно подключать, но оставляем — пригодится для snackbar.
     */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * Единый флаг: ручной режим активен для текущего activeProjectId.
     */
    val isManualMode: StateFlow<Boolean> =
        activeProjectDs.activeProjectId
            .distinctUntilChanged()
            .filterNotNull()
            .flatMapLatest { projectId -> manualRepo.observeSession(projectId) }
            .map { session -> session?.manualModeActive == true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Клик по "Ручной режим" в AppBar.
     * Логика:
     * - если нет доступа → paywall
     * - если уже активен → просто сообщаем (без повторного enter)
     * - иначе строим baseState из БД и входим
     */
    fun onManualModeClick() {
        val plan = userPlanRepository.planFlow.value
        if (!plan.capabilities.phaseDragAndDrop) {
            paywallBus.request(ProFeature.PHASE_DND_TEASER)
            return
        }

        viewModelScope.launch(ioDispatcher) {
            val projectId = activeProjectDs.activeProjectId.first().orEmpty()
            if (projectId.isBlank()) {
                messages.tryEmit("Не выбран проект")
                return@launch
            }

            // Если уже в manual — повторно не входим.
            val currentSession = manualRepo.getActiveSession()
            if (currentSession?.projectId == projectId && currentSession.manualModeActive) {
                messages.tryEmit("Ручной режим уже включён")
                return@launch
            }

            try {
                // ✅ baseState строим строго из репозитория/БД (детерминированный вход)
                val base = projectBaseStateBuilder.build(projectId)

                val TAG = "MANUAL_TOGGLE"

                Log.d(TAG, "click projectId=$projectId, activeSession=${manualRepo.getActiveSession()?.projectId}")

                manualRepo.enterManualMode(projectId = projectId, baseState = base)

                Log.d(TAG, "base built: groups=${base.groups.size} devices=${base.devices.size}")

                val s = manualRepo.getActiveSession()
                Log.d(TAG, "entered: sessionPid=${s?.projectId} active=${s?.manualModeActive} groups=${s?.draftState?.groups?.size}")

                // ✅ kill-process UX: ставим маркер "manual ожидается"
                manualDraftResetNotifier.markExpected(projectId)

                messages.tryEmit("Ручной режим включён")
            } catch (_: Throwable) {
                messages.tryEmit("Не удалось включить ручной режим")
            }
        }
    }
}