package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.use_case.OpenDemoProjectUseCase

@HiltViewModel
class DemoProjectViewModel @Inject constructor(
    private val openDemoProject: OpenDemoProjectUseCase,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    fun open() {
        if (mutableState.value.isLoading) return
        viewModelScope.launch {
            mutableState.value = UiState(isLoading = true)
            openDemoProject().fold(
                onSuccess = { mutableState.value = UiState() },
                onFailure = {
                    mutableState.value = UiState(
                        errorMessage = "Не удалось подготовить демо-проект. Попробуйте ещё раз."
                    )
                }
            )
        }
    }
}
