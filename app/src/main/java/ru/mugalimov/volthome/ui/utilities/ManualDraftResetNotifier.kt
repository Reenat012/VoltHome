package ru.mugalimov.volthome.ui.utilities

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Маркер для UX после убийства процесса (kill-process).
 *
 * Идея:
 * - При входе в manual ставим флаг "manual ожидается" для projectId.
 * - При нормальном завершении manual (Save/Cancel) флаг снимаем.
 * - При старте/смене активного проекта: если флаг стоит, но активной manual-сессии нет,
 *   значит процесс был убит и черновик потерян => показываем snackbar "Черновик ... был сброшен"
 *   и снимаем флаг (one-shot).
 */
@Singleton
class ManualDraftResetNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.getSharedPreferences("manual_draft_notifier", Context.MODE_PRIVATE)
    }

    private fun key(projectId: String) = "manual_expected__$projectId"

    /** Ставим маркер: пользователь вошёл в manual, значит при следующем запуске ожидаем сессию. */
    suspend fun markExpected(projectId: String) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(key(projectId), true).apply()
    }

    /** Снимаем маркер: manual завершён корректно (Save/Cancel). */
    suspend fun clearExpected(projectId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key(projectId)).apply()
    }

    /**
     * One-shot проверка на "черновик сброшен".
     *
     * @param hasActiveSession true если сейчас действительно есть активная manual-сессия для projectId.
     * @return true если нужно показать snackbar "Черновик ручного режима был сброшен".
     */
    suspend fun consumeResetIfNeeded(projectId: String, hasActiveSession: Boolean): Boolean =
        withContext(Dispatchers.IO) {
        val expected = prefs.getBoolean(key(projectId), false)
        if (!expected) return@withContext false

        // Флаг есть, но сессии нет => процесс убили/сессию потеряли.
        if (!hasActiveSession) {
            prefs.edit().remove(key(projectId)).apply()
            return@withContext true
        }
        false
    }
}
