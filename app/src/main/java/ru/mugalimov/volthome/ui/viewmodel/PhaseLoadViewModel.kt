package ru.mugalimov.volthome.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val result = getPhaseLoadUiUseCase()

                _uiState.update {
                    it.copy(
                        data = result,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e
                    )
                }
            }
        }
    }
}


