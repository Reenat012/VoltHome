package ru.mugalimov.volthome.ui.viewmodel

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import java.lang.Error

class OnboardingViewModel(
    private val repositoryPreferences: PreferencesRepository
) : ViewModel() {
    // Состояние UI
    data class UiState(
        val showOnboarding: Boolean = true, // Показывать ли онбординг
        val isLoading: Boolean = true, // Идет ли загрузка
        val error: String? = null // Ошибка, если есть
    )

    // Текущее состояние UI (изменяемое)
    private val _uiState = MutableStateFlow(UiState())

    // Публичное состояние (только для чтения)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // При создании viewmodel загружаем статус первого запуска
        loadFirstLaunchStatus()
    }

    // Загрузка статуса первого запуска из репозитория
    private fun loadFirstLaunchStatus() {
        viewModelScope.launch {
            repositoryPreferences.isFirstLaunch
                .catch { error ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            error = error.message ?: "Неизвестная ошибка",
                            isLoading = false
                        )
                    }
                }
                // Обновление состояния при получении данных
                .collect { isFirstLaunch ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            showOnboarding = isFirstLaunch,
                            isLoading = false
                        )
                    }
                }
        }
    }

    // Вызывается при завершении онбординга
    // Сохраняет статус и обновляет UI состояние
    fun completeOnboarding() {
        viewModelScope.launch {
            try {
                // Сохраняем в репозиторий, что онбординг завершен
                repositoryPreferences.completeOnboarding()

                // Обновляем состояние UI
                _uiState.update { it.copy(showOnboarding = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка сохранения настроек") }
            }
        }
    }
}