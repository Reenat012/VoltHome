package ru.mugalimov.volthome.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.use_case.RecalculateGroupsOnDeviceChangeUseCase
import javax.inject.Inject

@HiltViewModel
class AppSyncViewModel @Inject constructor(
    private val autoRecalc: RecalculateGroupsOnDeviceChangeUseCase
) : ViewModel() {

    init {
        // AUTO-пересчёт живёт столько же, сколько App-scoped VM.
        // Внутри usecase есть flatMapLatest по activeProjectId, поэтому cross-project не будет.
        autoRecalc.launch(
            scope = viewModelScope,
            onCalculated = { projectId: String, mode: PhaseMode, result: GroupingResult ->
                // ✅ Коммит №3: реактивный контур ТОЛЬКО считает и отдаёт результат наружу.
                // ❌ Никаких STRUCTURE writes здесь быть не должно (никаких replaceAll, никаких joins).
                when (result) {
                    is GroupingResult.Success -> {
                        Log.d(
                            "AUTO_RECALC_RESULT",
                            "pid=$projectId mode=${mode.name} OK groups=${result.system.groups.size}"
                        )
                        // Если хочешь — можешь тут обновлять in-memory UI state (StateFlow) для экрана,
                        // но НЕ писать структуру в БД.
                    }

                    is GroupingResult.Error -> {
                        Log.e(
                            "AUTO_RECALC_RESULT",
                            "pid=$projectId mode=${mode.name} ERROR msg=${result.message}"
                        )
                    }
                }
            }
        )
    }
}