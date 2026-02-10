package ru.mugalimov.volthome.ui.manual

/**
 * Состояние диалога guard'а.
 * Хранит отложенное действие (onProceed) и колбэки Save/Cancel.
 */
data class ManualModeGuardState(
    val isDialogVisible: Boolean = false,
    val action: ForbiddenAction? = null,
    val title: String = "",
    val message: String = "",
    val onProceed: (() -> Unit)? = null,
    val onSave: (() -> Unit)? = null,
    val onCancel: (() -> Unit)? = null,
)