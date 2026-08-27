package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.data.repository.resolve
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.reconfiguration.ProjectConfigurationDraft
import ru.mugalimov.volthome.domain.model.reconfiguration.ReconfigurationImpact
import ru.mugalimov.volthome.domain.model.reconfiguration.ReconfigurationOutcome
import ru.mugalimov.volthome.domain.use_case.reconfiguration.ApplyProjectReconfigurationUseCase
import ru.mugalimov.volthome.domain.use_case.reconfiguration.BuildProjectReconfigurationPreviewUseCase
import ru.mugalimov.volthome.domain.use_case.reconfiguration.UndoProjectReconfigurationUseCase

data class ProjectReconfigurationUiState(
    val isLoading: Boolean = true,
    val projectId: String = "",
    val phaseMode: PhaseMode = PhaseMode.THREE,
    val hasInputPower: Boolean = false,
    val inputPowerText: String = "",
    val preview: ReconfigurationImpact? = null,
    val isWorking: Boolean = false,
    val canUndo: Boolean = false,
    val backupLabel: String? = null,
    val error: String? = null
)

sealed interface ProjectReconfigurationEvent {
    data class Message(val text: String) : ProjectReconfigurationEvent
}

@HiltViewModel
class ProjectReconfigurationViewModel @Inject constructor(
    private val activeProjectDataStore: ActiveProjectDataStore,
    private val preferencesRepository: PreferencesRepository,
    private val setupRepository: ProjectSetupRepository,
    private val previewUseCase: BuildProjectReconfigurationPreviewUseCase,
    private val applyUseCase: ApplyProjectReconfigurationUseCase,
    private val undoUseCase: UndoProjectReconfigurationUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectReconfigurationUiState())
    val uiState: StateFlow<ProjectReconfigurationUiState> = _uiState

    private val _events = Channel<ProjectReconfigurationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        reload()
    }

    fun selectPhaseMode(mode: PhaseMode) {
        _uiState.update { it.copy(phaseMode = mode, preview = null, error = null) }
    }

    fun setInputPowerEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                hasInputPower = enabled,
                inputPowerText = if (enabled && it.inputPowerText.isBlank()) "15" else it.inputPowerText,
                preview = null,
                error = null
            )
        }
    }

    fun setInputPower(text: String) {
        if (text.length > 8) return
        if (text.isNotEmpty() && text.any { it !in "0123456789,." }) return
        _uiState.update { it.copy(inputPowerText = text, preview = null, error = null) }
    }

    fun buildPreview() {
        val draft = draftOrShowError() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            previewUseCase(draft).fold(
                onSuccess = { impact ->
                    _uiState.update { it.copy(isWorking = false, preview = impact) }
                },
                onFailure = { failure ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            preview = null,
                            error = failure.message ?: "Не удалось рассчитать изменения"
                        )
                    }
                }
            )
        }
    }

    fun applyChanges() {
        val draft = draftOrShowError() ?: return
        if (_uiState.value.preview == null) {
            _uiState.update { it.copy(error = "Сначала сформируйте предварительный результат") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            when (val outcome = applyUseCase(draft)) {
                is ReconfigurationOutcome.Success -> {
                    _events.send(
                        ProjectReconfigurationEvent.Message(
                            if (outcome.impact.requiresStructuralRebuild) {
                                "Параметры сети применены, линии пересчитаны"
                            } else {
                                "Доступная мощность проекта обновлена"
                            }
                        )
                    )
                    reload(showLoading = false)
                }
                is ReconfigurationOutcome.Failure -> {
                    _uiState.update { it.copy(isWorking = false, error = outcome.message) }
                }
            }
        }
    }

    fun undoLastReconfiguration() {
        val projectId = _uiState.value.projectId
        if (projectId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            undoUseCase(projectId).fold(
                onSuccess = {
                    _events.send(ProjectReconfigurationEvent.Message("Предыдущая конфигурация восстановлена"))
                    reload(showLoading = false)
                },
                onFailure = { failure ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            error = failure.message ?: "Не удалось отменить изменения"
                        )
                    }
                }
            )
        }
    }

    private fun reload(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(isLoading = true) }
            runCatching {
                val projectId = activeProjectDataStore.activeProjectId.first().orEmpty().trim()
                require(projectId.isNotBlank()) { "Сначала выберите проект" }
                val fallback = preferencesRepository.phaseMode.first()
                val setup = setupRepository.resolve(projectId, fallback)
                val backupAt = undoUseCase.backupCreatedAt(projectId)
                ProjectReconfigurationUiState(
                    isLoading = false,
                    projectId = projectId,
                    phaseMode = setup.phaseMode,
                    hasInputPower = setup.inputPowerKw != null,
                    inputPowerText = setup.inputPowerKw?.let(::formatPower).orEmpty(),
                    canUndo = undoUseCase.hasBackup(projectId),
                    backupLabel = backupAt?.let(::formatBackupTime)
                )
            }.fold(
                onSuccess = { state -> _uiState.value = state },
                onFailure = { failure ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isWorking = false,
                            error = failure.message ?: "Не удалось открыть параметры проекта"
                        )
                    }
                }
            )
        }
    }

    private fun draftOrShowError(): ProjectConfigurationDraft? {
        val state = _uiState.value
        val inputPower = if (state.hasInputPower) {
            state.inputPowerText.replace(',', '.').toDoubleOrNull()
        } else {
            null
        }
        if (state.hasInputPower && inputPower == null) {
            _uiState.update { it.copy(error = "Укажите доступную мощность в кВт") }
            return null
        }
        return ProjectConfigurationDraft(
            projectId = state.projectId,
            phaseMode = state.phaseMode,
            inputPowerKw = inputPower
        )
    }

    private fun formatPower(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(Locale.US, value)

    private fun formatBackupTime(epochMs: Long): String =
        SimpleDateFormat("dd.MM, HH:mm", Locale.getDefault()).format(Date(epochMs))
}
