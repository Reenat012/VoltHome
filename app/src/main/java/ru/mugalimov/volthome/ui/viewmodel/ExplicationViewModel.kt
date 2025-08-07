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
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import javax.inject.Inject

@HiltViewModel
class ExplicationViewModel @Inject constructor(
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val incomerSelector: IncomerSelector
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupScreenState>(GroupScreenState.Loading)
    val uiState: StateFlow<GroupScreenState> = _uiState.asStateFlow()

    fun calculateGroups() {
        viewModelScope.launch {
            _uiState.value = GroupScreenState.Loading
            val calc = groupCalculatorFactory.create()
            when (val res = calc.calculateGroups()) {
                is GroupingResult.Error -> _uiState.value = GroupScreenState.Error(res.message)
                is GroupingResult.Success -> {
                    val groups = res.system.groups

                    val incomer = incomerSelector.select(
                        IncomerSelector.Params(
                            groups = groups,
                            preferRcbo = false,   // позже привяжем к UI
                            hasGroupRcds = true   // позже привяжем к UI
                        )
                    )

                    _uiState.value = GroupScreenState.Success(
                        groups = groups,
                        totalGroups = groups.size,
                        totalCurrent = groups.sumOf { it.nominalCurrent },
                        incomer = incomer
                    )
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
        val totalCurrent: Double,
        val incomer: ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
    ) : GroupScreenState()

    data class Error(val message: String) : GroupScreenState()
}

data class GroupUiState(
    val groups: List<CircuitGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)