package ru.mugalimov.volthome.ui.manual

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository

/**
 * Единый guard для запретных действий во время manual-mode.
 *
 * Политика:
 * - если manual не активен -> действие выполняется сразу
 * - если manual активен -> показываем диалог Save/Cancel/Stay
 *   - Stay: ничего не делаем
 *   - Save: вызываем onSave (позже — реальный Save), затем выполняем действие
 *   - Cancel: вызываем onCancel (позже — реальный Cancel), затем выполняем действие
 */
class ManualModeGuard(
    private val manualRepo: ManualEditSessionRepository
) {
    private val _state = MutableStateFlow(ManualModeGuardState())
    val state: StateFlow<ManualModeGuardState> = _state.asStateFlow()

    fun isManualActive(): Boolean {
        return manualRepo.getActiveSession()?.manualModeActive == true
    }

    fun request(
        action: ForbiddenAction,
        onProceed: () -> Unit,
        onSave: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        if (!isManualActive()) {
            onProceed()
            return
        }

        val (title, message) = buildDialogText(action)

        _state.value = ManualModeGuardState(
            isDialogVisible = true,
            action = action,
            title = title,
            message = message,
            onProceed = onProceed,
            onSave = onSave,
            onCancel = onCancel
        )
    }

    fun dismiss() {
        _state.value = ManualModeGuardState()
    }

    fun onStay() {
        dismiss()
    }

    fun onSave() {
        val st = _state.value
        dismiss()
        st.onSave?.invoke()
        st.onProceed?.invoke()
    }

    fun onCancel() {
        val st = _state.value
        dismiss()
        st.onCancel?.invoke()
        st.onProceed?.invoke()
    }

    private fun buildDialogText(action: ForbiddenAction): Pair<String, String> {
        return when (action) {
            ForbiddenAction.SWITCH_PROJECT -> {
                "Ручные изменения не сохранены" to
                        "Сейчас активен ручной режим проекта.\n\n" +
                        "Если переключить проект, несохранённые изменения будут потеряны или приведут к рассинхронизации.\n\n" +
                        "Выберите действие:"
            }

            ForbiddenAction.EXPORT_PDF -> {
                "Ручные изменения не сохранены" to
                        "Сейчас активен ручной режим проекта.\n\n" +
                        "Экспорт/отчёт должен строиться из зафиксированного состояния.\n\n" +
                        "Выберите действие:"
            }

            ForbiddenAction.APPLY_INCOMING_SYNC -> {
                "Ручные изменения не сохранены" to
                        "Сейчас активен ручной режим проекта.\n\n" +
                        "Входящий sync может перезаписать данные и сделать черновик недействительным.\n\n" +
                        "Выберите действие:"
            }
        }
    }

    /**
     * EntryPoint, чтобы получить ManualEditSessionRepository из Composable без правок DI модулей.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ManualEditEntryPoint {
        fun manualEditSessionRepository(): ManualEditSessionRepository
    }

    companion object {
        fun fromApp(context: Context): ManualModeGuard {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ManualEditEntryPoint::class.java
            )
            return ManualModeGuard(ep.manualEditSessionRepository())
        }
    }
}