package ru.mugalimov.volthome.ui.viewmodel

import android.content.ContentValues.TAG
import android.nfc.Tag
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.core.error.GroupNotFoundException
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.use_case.GroupCalculator
import javax.inject.Inject

@HiltViewModel
class ExplicationViewModel @Inject constructor(
    private val explicationRepository: ExplicationRepository,
    private val calcGroup: GroupCalculator
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()


    init {
        loadGroups()
        Log.d(TAG, "Groups in viewModel ${uiState.value.groups}")


    }

    private fun loadGroups() {
        viewModelScope.launch {
            explicationRepository.observeAllGroup()
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e
                        )
                    }
                }
                .collect { groups ->
                    _uiState.update {
                        it.copy(
                            groups = groups,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    suspend fun getAllGroup() {
        viewModelScope.launch {
            try {
                val groupsTest = explicationRepository.getAllGroups()

                Log.d(TAG, "Тест групп во viewModel getAllGroups: $groupsTest")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при получении групп: ", e)

            }
        }
    }

    fun calcGroups() {
        viewModelScope.launch {
//            val electicalSystem = calcGroup.calculateGroups()
//
//            val groups = electicalSystem.circuitGroups
//            Log.d(TAG, "Группы в calcGroups из useCase $groups")
//
//            explicationRepository.addGroup(groups)

            val electicalSystem = calcGroup.calculateGroups()
            val groups = electicalSystem.circuitGroups

            // Очищаем старые группы перед добавлением новых
            explicationRepository.deleteAllGroups()
            explicationRepository.addGroup(groups)

        }
    }


}

data class GroupUiState(
    val groups: List<CircuitGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: Throwable? = null
)