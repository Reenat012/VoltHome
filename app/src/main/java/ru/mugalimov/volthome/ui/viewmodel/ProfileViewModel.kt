package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.AuthRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.UserProfile

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPlanRepository: UserPlanRepository
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Data(val me: UserProfile) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            userPlanRepository.planFlow.collect { plan ->
                loadProfile(plan.plan, plan.planUntilEpochSeconds)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val plan = userPlanRepository.planFlow.value
            loadProfile(plan.plan, plan.planUntilEpochSeconds)
        }
    }

    private suspend fun loadProfile(plan: String, planUntilEpochSeconds: Long?) {
        val session = authRepository.currentSession()
        if (session == null) {
            _state.value = UiState.Error("Локальная сессия не найдена.")
            return
        }

        _state.value = UiState.Data(
            UserProfile(
                displayName = session.displayName,
                email = session.email,
                avatarUrl = session.avatarUrl,
                plan = plan,
                planUntilEpochSeconds = planUntilEpochSeconds
            )
        )
    }
}
