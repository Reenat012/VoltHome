package ru.mugalimov.volthome.ui.screens.loads

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import ru.mugalimov.volthome.domain.use_case.GetPhaseLoadUiUseCase
import javax.inject.Inject

data class PhaseLoadUiState(
    val isLoading: Boolean = false,
    val data: List<PhaseLoadItem> = emptyList(),
    val error: Throwable? = null
)

@HiltViewModel
class PhaseLoadViewModel @Inject constructor(
    getPhaseLoadUiUseCase: GetPhaseLoadUiUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {



    // Объединяем поток UI-данных с режимом и скрываем B/C в 1-ф
    val uiState: StateFlow<PhaseLoadUiState> =
        combine(
            getPhaseLoadUiUseCase(),
            preferencesRepository.phaseMode
        ) { items, mode ->
            val data = if (mode == PhaseMode.SINGLE) {
                items.filter { it.phase == Phase.A }
            } else items
            PhaseLoadUiState(data = data)
        }
            .onStart { emit(PhaseLoadUiState(isLoading = true)) }
            .catch { emit(PhaseLoadUiState(error = it)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PhaseLoadUiState(isLoading = true)
            )

}