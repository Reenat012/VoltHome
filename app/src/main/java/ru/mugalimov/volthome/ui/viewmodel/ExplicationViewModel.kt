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
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import javax.inject.Inject

@HiltViewModel
class ExplicationViewModel @Inject constructor(
    private val explicationRepository: ExplicationRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory
) : ViewModel() {

    // Состояние UI экрана групп
    private val _uiState = MutableStateFlow<GroupScreenState>(GroupScreenState.Loading)
    val uiState: StateFlow<GroupScreenState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            explicationRepository.observeAllGroup().collect { groups ->
                _uiState.value = GroupScreenState.Success(
                    groups = groups,
                    totalGroups = groups.size,
                    totalCurrent = groups.sumOf { it.nominalCurrent }
                )
            }
        }
    }

//    private fun loadGroups() {
//        viewModelScope.launch {
//            explicationRepository.observeAllGroup()
//                .onStart { _uiState.update { it.copy(isLoading = true) } }
//                .catch { e ->
//                    _uiState.update {
//                        it.copy(
//                            isLoading = false,
//                            error = e
//                        )
//                    }
//                }
//                .collect { groups ->
//                    _uiState.update {
//                        it.copy(
//                            groups = groups,
//                            isLoading = false,
//                            error = null
//                        )
//                    }
//                }
//        }
//    }

    fun getAllGroups() {
        viewModelScope.launch {
            try {
                val groups = explicationRepository.getAllGroups()
                Log.d(TAG, "Groups: $groups")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading groups", e)
            }
        }
    }

    /**
     * Запускает расчет электрических групп
     */
    fun calculateGroups() {
        viewModelScope.launch {
            _uiState.value = GroupScreenState.Loading
            val calcGroup = groupCalculatorFactory.create()
            calcGroup.calculateGroups()
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