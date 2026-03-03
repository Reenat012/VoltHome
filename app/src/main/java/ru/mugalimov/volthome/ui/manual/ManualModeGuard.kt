package ru.mugalimov.volthome.ui.manual

import android.content.Context
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.use_case.manual.CommitManualDraftToLocalDbUseCase

/**
 * ManualModeGuard — единая точка запрета "опасных" действий в manual.
 *
 * Задачи:
 * - Если manual НЕ активен: выполняем действие сразу.
 * - Если manual активен: показываем единый диалог Save / Cancel / Stay.
 *
 * Важно:
 * - request() НЕ должен блокировать UI: проверка manual-сессии делается в фоне.
 * - Добавлена защита от повторных нажатий (isProcessing) — иначе гонки и двойные execute().
 *
 * Коммит 8:
 * - После Save/Cancel обязательно чистим group_phase_overrides,
 *   иначе AUTO перетрёт сохранённые фазы и пользователь увидит "откат".
 */
@Singleton
class ManualModeGuard private constructor(
    private val manualRepo: ManualEditSessionRepository,
    private val commitManualDraftToLocalDb: CommitManualDraftToLocalDbUseCase,
    private val groupPhaseOverrideDao: GroupPhaseOverrideDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _dialogState = MutableStateFlow<DialogState?>(null)
    val dialogState: StateFlow<DialogState?> = _dialogState.asStateFlow()

    data class DialogState(
        val action: ForbiddenAction,
        val onProceed: () -> Unit,
        val isProcessing: Boolean = false,
    )

    fun dismiss() {
        scope.launch {
            withContext(Dispatchers.Main.immediate) {
                _dialogState.value = null
            }
        }
    }

    fun request(
        action: ForbiddenAction,
        onProceed: () -> Unit,
        forceDialog: Boolean = false,
    ) {
        if (forceDialog) {
            scope.launch {
                withContext(Dispatchers.Main.immediate) {
                    _dialogState.value = DialogState(action = action, onProceed = onProceed)
                }
            }
            return
        }

        scope.launch {
            val s = runCatching { manualRepo.getActiveSession() }.getOrNull()
            val manualActive = (s?.manualModeActive == true)

            withContext(Dispatchers.Main.immediate) {
                if (!manualActive) {
                    onProceed()
                } else {
                    _dialogState.value = DialogState(action = action, onProceed = onProceed)
                }
            }
        }
    }

    fun onSaveClicked() {
        val state = _dialogState.value ?: return
        if (state.isProcessing) return

        _dialogState.value = state.copy(isProcessing = true)

        scope.launch {
            val session = runCatching { manualRepo.getActiveSession() }.getOrNull()
            if (session == null) {
                withContext(Dispatchers.Main.immediate) {
                    _dialogState.value = null
                    state.onProceed()
                }
                return@launch
            }

            val projectId = session.projectId

            val ok = runCatching {
                // 1) commit draft -> локальная БД (diff-commit)
                commitManualDraftToLocalDb.execute(
                    CommitManualDraftToLocalDbUseCase.Params(
                        projectId = projectId,
                        draft = session.draftState
                    )
                )

                // 2) КРИТИЧНО: чистим overrides, иначе AUTO "откатается" поверх сохранённых фаз
                val deleted = groupPhaseOverrideDao.deleteByProject(projectId)
                Log.w(TAG, "DELETE overrides pid=$projectId (guard SAVE) deletedRows=$deleted")

                // 3) Выходим из manual
                manualRepo.exitManualMode(projectId)
            }.onFailure {
                Log.e(TAG, "onSaveClicked failed. projectId=$projectId", it)
            }.isSuccess

            withContext(Dispatchers.Main.immediate) {
                if (ok) {
                    _dialogState.value = null
                    state.onProceed()
                } else {
                    // Ошибка — остаёмся в manual, возвращаем кнопки
                    _dialogState.value = state.copy(isProcessing = false)
                }
            }
        }
    }

    fun onCancelClicked() {
        val state = _dialogState.value ?: return
        if (state.isProcessing) return

        _dialogState.value = state.copy(isProcessing = true)

        scope.launch {
            val session = runCatching { manualRepo.getActiveSession() }.getOrNull()

            if (session == null) {
                withContext(Dispatchers.Main.immediate) {
                    _dialogState.value = null
                    state.onProceed()
                }
                return@launch
            }

            val projectId = session.projectId

            val ok = runCatching {
                // ✅ 1) чистим overrides (это не auto-recalc, не запись групп)
                val deleted = groupPhaseOverrideDao.deleteByProject(projectId)
                Log.w(TAG, "DELETE overrides pid=$projectId (guard CANCEL) deletedRows=$deleted")

                // ✅ 2) выходим из manual (draft отбрасывается логикой repo)
                manualRepo.exitManualMode(projectId)
            }.onFailure {
                Log.e(TAG, "onCancelClicked failed. projectId=$projectId", it)
            }.isSuccess

            withContext(Dispatchers.Main.immediate) {
                if (ok) {
                    _dialogState.value = null
                    state.onProceed()
                } else {
                    _dialogState.value = state.copy(isProcessing = false)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ManualModeGuard"

        fun fromApp(appContext: Context): ManualModeGuard {
            val ep = EntryPointAccessors.fromApplication(
                appContext,
                ManualModeGuardEntryPoint::class.java
            )

            return ManualModeGuard(
                manualRepo = ep.manualRepo(),
                commitManualDraftToLocalDb = ep.commitManualDraftToLocalDb(),
                groupPhaseOverrideDao = ep.groupPhaseOverrideDao()
            )
        }
    }
}