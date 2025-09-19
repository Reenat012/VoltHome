package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.remote.api.ProfileMeDto
import ru.mugalimov.volthome.data.repository.UserRepository
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: UserRepository
) : ViewModel() {

    sealed interface UiState {
        object Loading : UiState
        data class Data(val me: ProfileMeDto) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val res = repo.loadMe()
            _state.value = res.fold(
                onSuccess = { UiState.Data(it) },
                onFailure = {
                    val msg = when (it.message) {
                        "no_token" -> "Не выполнен вход."
                        "invalid_refresh" -> "Сессия истекла. Войдите снова."
                        else -> it.message ?: "Ошибка загрузки профиля."
                    }
                    UiState.Error(msg)
                }
            )
        }
    }
}