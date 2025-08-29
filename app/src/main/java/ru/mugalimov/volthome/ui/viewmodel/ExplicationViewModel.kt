package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.report.ReportDevice
import ru.mugalimov.volthome.domain.model.report.ReportGroup
import ru.mugalimov.volthome.domain.model.report.ReportMeta
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import ru.mugalimov.volthome.domain.use_case.getOrZero
import ru.mugalimov.volthome.domain.use_case.phaseCurrents
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExplicationViewModel @Inject constructor(
    private val repo: ExplicationRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory,
    @IoDispatcher private val dispatchers: CoroutineDispatcher
) : ViewModel() {
    private val _uiState = MutableStateFlow<GroupScreenState>(GroupScreenState.Loading)
    val uiState: StateFlow<GroupScreenState> = _uiState

    private val _isRecalculating = MutableStateFlow(false)
    val isRecalculating: StateFlow<Boolean> = _isRecalculating

    fun recalcAndSaveGroups() {
        viewModelScope.launch(dispatchers) {
            _uiState.value = GroupScreenState.Loading            // ← явно показываем загрузку
            try {
                val calc = groupCalculatorFactory.create()
                when (val res = calc.calculateGroups()) {
                    is GroupingResult.Error -> {
                        _uiState.value = GroupScreenState.Error(res.message)
                    }
                    is GroupingResult.Success -> {
                        val groups: List<CircuitGroup> = res.system.groups

                        // 1) Сохраняем в БД
                        repo.replaceAllGroupsTransactional(groups)

                        // 2) Готовим метаданные для экрана (инкомер и пр.)
                        val totalGroups = groups.size
                        val totalCurrent = groups.sumOf { it.nominalCurrent }
                        val hasGroupRcds = groups.any { it.rcdRequired }

                        val incomer = IncomerSelector().select(
                            IncomerSelector.Params(
                                groups = groups,
                                preferRcbo = false,          // если нужно — поднимем в Settings
                                hasGroupRcds = hasGroupRcds
                            )
                        )

                        // 3) Отдаём на экран именно “распределённые” группы
                        _uiState.value = GroupScreenState.Success(
                            groups = groups,
                            totalGroups = totalGroups,
                            totalCurrent = totalCurrent,
                            incomer = incomer,
                            hasGroupRcds = hasGroupRcds
                        )
                    }
                }
            } catch (t: Throwable) {
                _uiState.value = GroupScreenState.Error(t.message ?: "Неизвестная ошибка")
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
        val incomer: ru.mugalimov.volthome.domain.model.incomer.IncomerSpec,
        val hasGroupRcds: Boolean
    ) : GroupScreenState()

    data class Error(val message: String) : GroupScreenState()
}

fun ExplicationViewModel.buildReportData(): Pair<ReportMeta, List<ReportPhase>>? {
    val s = uiState.value as? GroupScreenState.Success ?: return null
    val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(System.currentTimeMillis())

    val perPhase = phaseCurrents(s.groups)
    val meta = ReportMeta(
        date = date,
        incomer = with(s.incomer) {
            // Собираем короткое описание вводного аппарата
            buildString {
                append(kind.name)    // RCBO/MCB_ONLY/MCB_PLUS_RCD
                append(", ")
                append("${poles}P, ")
                append("${mcbRating}A ${mcbCurve}, Icn ${icn}")
                rcdType?.let { append(", RCD ${it} ${rcdSensitivityMa ?: 30}mA") }
            }
        },
        totalGroups = s.totalGroups,
        totalCurrent = s.totalCurrent,
        phaseCurrents = mapOf(
            "A" to perPhase.getOrZero(Phase.A),
            "B" to perPhase.getOrZero(Phase.B),
            "C" to perPhase.getOrZero(Phase.C)
        )
    )

    // Группируем по фазам и готовим секции
    val phases = s.groups
        .groupBy { it.phase }
        .toSortedMap(compareBy { it.name }) // A,B,C
        .map { (phase, groups) ->
            ReportPhase(
                name = "Фаза ${phase.name}",
                groups = groups.sortedBy { it.groupNumber }.map { g ->
                    ReportGroup(
                        title = "Группа #${g.groupNumber} — ${g.roomName}",
                        devices = g.devices.map { d ->
                            val pKw = d.power / 1000.0
                            val iA = d.current
                            ReportDevice(
                                name = d.name,
                                spec = "${"%.1f".format(pKw)} кВт, ${"%.1f".format(iA)} А"
                            )
                        }
                    )
                }
            )
        }

    return meta to phases
}