package ru.mugalimov.volthome.ui.utilities

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Маркер для UX после убийства процесса.
 *
 * Идея:
 * - При входе в manual ставим флаг "draft ожидается" для projectId.
 * - При нормальном завершении (Save/Cancel) флаг снимаем.
 * - При старте экрана/проекта: если флаг стоит, но manual-сессии нет => процесс был убит,
 *   показываем уведомление "Черновик был сброшен" и снимаем флаг (one-shot).
 */
@Singleton
class ManualDraftResetNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("manual_draft_notifier", Context.MODE_PRIVATE)

    private fun key(projectId: String) = "manual_expected__$projectId"

    /** Ставим маркер: пользователь вошёл в manual, значит ожидаем сессию. */
    fun markExpected(projectId: String) {
        prefs.edit().putBoolean(key(projectId), true).apply()
    }

    /** Снимаем маркер: manual завершён корректно (Save/Cancel). */
    fun clearExpected(projectId: String) {
        prefs.edit().remove(key(projectId)).apply()
    }

    /**
     * One-shot проверка:
     * @return true если нужно показать уведомление о сбросе черновика.
     */
    fun consumeResetIfNeeded(projectId: String, hasActiveSession: Boolean): Boolean {
        val expected = prefs.getBoolean(key(projectId), false)
        if (!expected) return false

        // Если флаг есть, но сессии уже нет — значит процесс убили/сессия потеряна.
        if (!hasActiveSession) {
            clearExpected(projectId)
            return true
        }
        return false
    }
}