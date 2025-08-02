package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadUiState
import ru.mugalimov.volthome.domain.use_case.GetPhaseLoadUiUseCase
import javax.inject.Inject

@HiltViewModel
class PhaseLoadViewModel @Inject constructor(
    private val getPhaseLoadUiUseCase: GetPhaseLoadUiUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhaseLoadUiState())
    val uiState: StateFlow<PhaseLoadUiState> = _uiState.asStateFlow()

    init {
        loadPhaseLoads()
    }

    private fun loadPhaseLoads() {
        viewModelScope.launch {
            try {
                val result = getPhaseLoadUiUseCase()
                _uiState.value = PhaseLoadUiState(
                    isLoading = false,
                    data = result
                )
            } catch (e: Exception) {
                _uiState.value = PhaseLoadUiState(
                    isLoading = false,
                    error = e
                )
            }
        }
    }
}
