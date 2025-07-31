package ru.mugalimov.volthome.ui.viewmodel

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import javax.inject.Inject

@HiltViewModel
class ExplicationViewModel @Inject constructor(
    private val groupCalculatorFactory: GroupCalculatorFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupScreenState>(GroupScreenState.Loading)
    val uiState: StateFlow<GroupScreenState> = _uiState.asStateFlow()

    fun calculateGroups() {
        viewModelScope.launch {
            Log.d("VM", "Запускаем расчет...")
            _uiState.value = GroupScreenState.Loading

            val calculator = groupCalculatorFactory.create()
            val result = calculator.calculateGroups()
            Log.d("VM", "Расчет завершён!")

            when (result) {
                is GroupingResult.Success -> {
                    _uiState.value = GroupScreenState.Success(
                        groups = result.system.groups,
                        totalGroups = result.system.groups.size,
                        totalCurrent = result.system.groups.sumOf { it.nominalCurrent }
                    )
                }

                is GroupingResult.Error -> {
                    _uiState.value = GroupScreenState.Error(result.message)
                }
            }
        }
    }
}



/**
 * Состояния UI экрана групп:
 * - Loading: данные загружаются
 * - Success: успешный расчет с данными групп
 * - Error: ошибка расчета с сообщением
 */
sealed class GroupScreenState {
    object Loading : GroupScreenState()
    data class Success(
        val groups: List<CircuitGroup>,
        val totalGroups: Int,
        val totalCurrent: Double
    ) : GroupScreenState()

    data class Error(val message: String) : GroupScreenState()
}

data class GroupUiState(
    val groups: List<CircuitGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)