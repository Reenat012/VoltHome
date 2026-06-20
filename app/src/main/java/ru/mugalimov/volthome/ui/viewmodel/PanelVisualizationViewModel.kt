package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.panel.PanelVisualization
import ru.mugalimov.volthome.domain.use_case.GeneratePanelVisualizationUseCase
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import javax.inject.Inject

/**
 * Состояние экрана визуализации щита.
 *
 * В MVP есть только три состояния:
 * - загрузка;
 * - готовая визуализация;
 * - пустое состояние, когда групп ещё нет.
 */
sealed interface PanelVisualizationUiState {

    data object Loading : PanelVisualizationUiState

    data class Content(
        val panel: PanelVisualization
    ) : PanelVisualizationUiState

    data object Empty : PanelVisualizationUiState
}

/**
 * ViewModel экрана "Визуализация щита".
 *
 * Важно:
 * ViewModel не пересчитывает группы и не меняет экспликацию.
 * Она только берёт уже рассчитанные группы активного проекта
 * и передаёт их в use-case сборки модели визуализации.
 */
@HiltViewModel
class PanelVisualizationViewModel @Inject constructor(
    private val activeProjectDataStore: ActiveProjectDataStore,
    private val explicationRepository: ExplicationRepository,
    private val incomerSelector: IncomerSelector,
    private val generatePanelVisualizationUseCase: GeneratePanelVisualizationUseCase
) : ViewModel() {

    val uiState: StateFlow<PanelVisualizationUiState> =
        activeProjectDataStore.activeProjectId
            .distinctUntilChanged()
            .flatMapLatest { projectId ->
                val normalizedProjectId = projectId.orEmpty().trim()

                if (normalizedProjectId.isBlank()) {
                    flowOf(PanelVisualizationUiState.Empty)
                } else {
                    explicationRepository
                        .observeAllGroupByProject(normalizedProjectId)
                        .map { groups ->
                            if (groups.isEmpty()) {
                                PanelVisualizationUiState.Empty
                            } else {
                                // Вводной аппарат берём через уже существующий selector.
                                // Повторный расчёт групп здесь не выполняется.
                                val incomer = incomerSelector.select(
                                    IncomerSelector.Params(
                                        groups = groups
                                    )
                                )

                                val panel = generatePanelVisualizationUseCase(
                                    incomer = incomer,
                                    groups = groups
                                )

                                PanelVisualizationUiState.Content(
                                    panel = panel
                                )
                            }
                        }
                        .onStart {
                            emit(PanelVisualizationUiState.Loading)
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PanelVisualizationUiState.Loading
            )
}