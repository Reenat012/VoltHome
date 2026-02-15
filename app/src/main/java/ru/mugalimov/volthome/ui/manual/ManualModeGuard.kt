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
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.use_case.manual.CancelManualAndAutoRecalcUseCase
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
 */
@Singleton
class ManualModeGuard private constructor(
    private val manualRepo: ManualEditSessionRepository,
    private val commitManualDraftToLocalDb: CommitManualDraftToLocalDbUseCase,
    private val cancelManualAndAutoRecalc: CancelManualAndAutoRecalcUseCase,
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
        // Важно: UI-стейт трогаем на Main, чтобы не ловить “мелькание”/гонки в Compose.
        scope.launch {
            withContext(Dispatchers.Main.immediate) {
                _dialogState.value = null
            }
        }
    }

    /**
     * Главная точка входа.
     *
     * @param forceDialog true = показать диалог без проверки getActiveSession().
     * Нужен, когда UI уже ТОЧНО знает, что manual включён (observeSession(projectId)).
     */
    fun request(
        action: ForbiddenAction,
        onProceed: () -> Unit,
        forceDialog: Boolean = false,
    ) {
        if (forceDialog) {
            // Вызывающая сторона гарантирует, что manual включён — сразу показываем диалог.
            scope.launch {
                withContext(Dispatchers.Main.immediate) {
                    _dialogState.value = DialogState(action = action, onProceed = onProceed)
                }
            }
            return
        }

        // ✅ Никаких синхронных запросов на UI-потоке — проверяем в фоне.
        scope.launch {
            val s = runCatching { manualRepo.getActiveSession() }.getOrNull()
            val manualActive = (s?.manualModeActive == true)

            withContext(Dispatchers.Main.immediate) {
                if (!manualActive) {
                    // Manual выключен — выполняем действие сразу.
                    onProceed()
                } else {
                    // Manual включён — показываем диалог.
                    _dialogState.value = DialogState(action = action, onProceed = onProceed)
                }
            }
        }
    }

    fun onSaveClicked() {
        val state = _dialogState.value ?: return
        if (state.isProcessing) return

        // Блокируем повторные нажатия.
        _dialogState.value = state.copy(isProcessing = true)

        scope.launch {
            val session = runCatching { manualRepo.getActiveSession() }.getOrNull()
            if (session == null) {
                // Сессии уже нет — просто закрываем и выполняем действие.
                withContext(Dispatchers.Main.immediate) {
                    _dialogState.value = null
                    state.onProceed()
                }
                return@launch
            }

            // ✅ ВАЖНО:
            // 1) Сначала пробуем commit (diff-commit).
            // 2) Выходим из manual ТОЛЬКО если commit успешен.
            // 3) Если commit упал — manual НЕ выключаем, возвращаем кнопки.
            val commitResult = runCatching {
                commitManualDraftToLocalDb.execute(
                    CommitManualDraftToLocalDbUseCase.Params(
                        projectId = session.projectId,
                        draft = session.draftState
                    )
                )
            }

            val ok = if (commitResult.isSuccess) {
                val exitOk = runCatching { manualRepo.exitManualMode(session.projectId) }.isSuccess
                if (!exitOk) {
                    Log.e(TAG, "onSaveClicked: commit ok, but exitManualMode failed. projectId=${session.projectId}")
                }
                exitOk
            } else {
                Log.e(TAG, "onSaveClicked: commit failed. projectId=${session.projectId}", commitResult.exceptionOrNull())
                false
            }

            withContext(Dispatchers.Main.immediate) {
                if (ok) {
                    _dialogState.value = null
                    state.onProceed()
                } else {
                    // Ошибка — остаёмся в manual, возвращаем кнопки.
                    _dialogState.value = state.copy(isProcessing = false)
                }
            }
        }
    }

    fun onCancelClicked() {
        val state = _dialogState.value ?: return
        if (state.isProcessing) return

        // Блокируем повторные нажатия.
        _dialogState.value = state.copy(isProcessing = true)

        scope.launch {
            val session = runCatching { manualRepo.getActiveSession() }.getOrNull()

            val ok = runCatching {
                if (session != null) {
                    // ✅ Сначала выходим из manual для этого проекта
                    manualRepo.exitManualMode(session.projectId)

                    // ✅ Затем запускаем авто-пересчёт именно по этому projectId
                    cancelManualAndAutoRecalc.execute(
                        CancelManualAndAutoRecalcUseCase.Params(projectId = session.projectId)
                    )
                }
                // Если session == null — manual уже не активен (или kill-process),
                // тут просто считаем "cancel" успешным (действие можно продолжать).
            }.onFailure {
                Log.e(TAG, "onCancelClicked failed. sessionProjectId=${session?.projectId}", it)
            }.isSuccess

            withContext(Dispatchers.Main.immediate) {
                if (ok) {
                    _dialogState.value = null
                    state.onProceed()
                } else {
                    // Ошибка — диалог оставляем, чтобы пользователь мог нажать "Остаться".
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
                cancelManualAndAutoRecalc = ep.cancelManualAndAutoRecalc()
            )
        }
    }
}