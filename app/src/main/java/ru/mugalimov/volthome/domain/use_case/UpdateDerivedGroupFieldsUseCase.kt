package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupNominalCurrentUpdate
import javax.inject.Inject

/**
 * Коммит 4:
 * Безопасный writer derived-полей групп (whitelist).
 *
 * Правило:
 * - пишем ТОЛЬКО derived-поля
 * - ТОЛЬКО по groupId
 * - с project boundary
 * - с идемпотентностью (rowsUpdated может быть 0 — это норм)
 */
class UpdateDerivedGroupFieldsUseCase @Inject constructor(
    private val groupDao: GroupDao
) {
    suspend fun updateNominalCurrentsOnly(
        projectId: String,
        updates: List<GroupNominalCurrentUpdate>,
        epsilon: Double = 1e-4
    ): Int = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) {
            Log.d("DERIVED_WRITE", "skip pid=$projectId reason=EMPTY")
            return@withContext 0
        }

        val expected = updates.size
        val rows = groupDao.updateDerivedNominalCurrentOnlyBatch(
            projectId = projectId,
            updates = updates,
            epsilon = epsilon
        )

        // rows может быть меньше expected — это и есть идемпотентность.
        Log.w(
            "DERIVED_WRITE",
            "nominal_current pid=$projectId expected=$expected rowsUpdated=$rows epsilon=$epsilon"
        )

        rows
    }
}