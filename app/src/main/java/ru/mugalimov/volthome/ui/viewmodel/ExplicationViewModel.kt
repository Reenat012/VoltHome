package ru.mugalimov.volthome.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.report.DonutModel
import ru.mugalimov.volthome.domain.model.report.ReportDevice
import ru.mugalimov.volthome.domain.model.report.ReportGroup
import ru.mugalimov.volthome.domain.model.report.ReportMeta
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import ru.mugalimov.volthome.domain.use_case.CalculateShieldOverviewUseCase
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import ru.mugalimov.volthome.domain.use_case.getOrZero
import ru.mugalimov.volthome.domain.use_case.phaseCurrents
import ru.mugalimov.volthome.domain.formatter.GroupMetaFormatter
import ru.mugalimov.volthome.domain.util.PowerCurrentNormalizer
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcWarning
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.DeviceCalcBreakdown
import ru.mugalimov.volthome.domain.use_case.CalculateDeviceBreakdownUseCase

@HiltViewModel
class ExplicationViewModel @Inject constructor(
    private val repo: ExplicationRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: PreferencesRepository,
    private val deviceRepository: DeviceRepository,
    private val calculateShieldOverviewUseCase: CalculateShieldOverviewUseCase,
    private val calculateDeviceBreakdownUseCase: CalculateDeviceBreakdownUseCase,
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    val userPlanRepository: UserPlanRepository,
    private val paywallBus: PaywallBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupScreenState>(GroupScreenState.Loading)
    val uiState: StateFlow<GroupScreenState> = _uiState

    private val _isRecalculating = MutableStateFlow(false)
    val isRecalculating: StateFlow<Boolean> = _isRecalculating

    // последний известный режим фаз (для buildReportData)
    private val _phaseMode = MutableStateFlow(PhaseMode.THREE)
    val phaseMode: StateFlow<PhaseMode> = _phaseMode.asStateFlow()

    // выбранный инстанс устройства для показа в шите
    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

    private val _selectedDeviceBreakdown = MutableStateFlow<DeviceCalcBreakdown?>(null)
    val selectedDeviceBreakdown: StateFlow<DeviceCalcBreakdown?> =
        _selectedDeviceBreakdown.asStateFlow()

    // --- UI events (one-shot) ---
    sealed class UiEvent {
        object ExportPdfRequested : UiEvent()
    }

    private val _events = MutableStateFlow<UiEvent?>(null)
    val events: StateFlow<UiEvent?> = _events.asStateFlow()

    // Необязательное авто-пересчитывание при смене режима:
    init {
        viewModelScope.launch(dispatchers) {
            preferencesRepository.phaseMode.collect { mode ->
                _phaseMode.value = mode
                recalcAndSaveGroups()
            }
        }
    }

    fun onExportPdfClick() {
        val plan = userPlanRepository.planFlow.value
        if (!plan.capabilities.pdfExport) {
            paywallBus.request(ProFeature.PRO_REPORT)
            return
        }
        _events.value = UiEvent.ExportPdfRequested
    }

    fun consumeEvent() {
        _events.value = null
    }

    /** ВАЖНО: берём ИНСТАНС устройства по id из репозитория, без дефолтов. */
    fun onDeviceClick(deviceId: Long) {viewModelScope.launch {
        _selectedDeviceBreakdown.value = null

        val dev = deviceRepository.getDeviceById(deviceId.toInt())
        _selectedDevice.value = dev

        val plan = userPlanRepository.planFlow.value
        if (plan.capabilities.professionalReportSections) {
            _selectedDeviceBreakdown.value =
                dev?.let { calculateDeviceBreakdownUseCase.execute(it) }
        }
    }
    }

    fun clearSelected() {
        _selectedDevice.value = null
        _selectedDeviceBreakdown.value = null
    }

    fun recalcAndSaveGroups() {
        viewModelScope.launch(dispatchers) {
            _uiState.value = GroupScreenState.Loading
            try {
                val mode: PhaseMode = preferencesRepository.phaseMode.first()

                val calc = groupCalculatorFactory.create()
                when (val res = calc.calculateGroups(mode)) {
                    is GroupingResult.Error -> {
                        _uiState.value = GroupScreenState.Error(res.message)
                    }

                    is GroupingResult.Success -> {
                        val groups: List<CircuitGroup> = res.system.groups

                        repo.replaceAllGroupsTransactional(groups)

                        // ✅ Коммит 9: decision log распределения фаз → в репозиторий (in-memory),
                        // чтобы PhaseLoad экран мог показать "почему так" по каждой группе.
                        repo.setLastDistributionDecisions(res.distributionDecisions)

                        val totalGroups = groups.size
                        val totalCurrent = groups.sumOf { it.nominalCurrent }
                        val hasGroupRcds = groups.any { it.rcdRequired }

                        val incomer = IncomerSelector().select(
                            IncomerSelector.Params(
                                groups = groups,
                                preferRcbo = false,
                                hasGroupRcds = hasGroupRcds,
                                voltageTypeOverride = when (mode) {
                                    PhaseMode.SINGLE -> VoltageType.AC_1PHASE
                                    PhaseMode.THREE -> VoltageType.AC_3PHASE
                                }
                            )
                        )

                        val plan = userPlanRepository.planFlow.value
                        val isProReport = plan.capabilities.professionalReportSections

                        val totals = calculateShieldOverviewUseCase.execute(groups)
                        val warnings = if (isProReport) {
                            buildWarningsFromGroups(groups)
                        } else {
                            emptyList()
                        }

                        val installedPower = if (isProReport) {
                            totals.installedPowerW
                        } else {
                            totals.installedPowerW.copy(
                                steps = emptyList(),
                                assumptions = emptyList(),
                                warnings = emptyList(),
                                normRefs = emptyList()
                            )
                        }

                        val calculatedPower = if (isProReport) {
                            totals.calculatedPowerW
                        } else {
                            totals.calculatedPowerW.copy(
                                steps = emptyList(),
                                assumptions = emptyList(),
                                warnings = emptyList(),
                                normRefs = emptyList()
                            )
                        }

                        _uiState.value = GroupScreenState.Success(
                            groups = groups,
                            totalGroups = totalGroups,
                            totalCurrent = totalCurrent,
                            incomer = incomer,
                            hasGroupRcds = hasGroupRcds,
                            installedPowerW = installedPower,
                            calculatedPowerW = calculatedPower,
                            shieldTotalsAssumptions = if (isProReport) {
                                calculatedPower.assumptions
                            } else {
                                emptyList()
                            },
                            calcWarnings = warnings
                        )
                    }
                }
            } catch (t: Throwable) {
                _uiState.value = GroupScreenState.Error(t.message ?: "Неизвестная ошибка")
            }
        }
    }
}

sealed class GroupScreenState {
    object Loading : GroupScreenState()

    data class Success(
        val groups: List<CircuitGroup>,
        val totalGroups: Int,
        val totalCurrent: Double,
        val incomer: IncomerSpec,
        val hasGroupRcds: Boolean,
        val installedPowerW: CalculatedValue,
        val calculatedPowerW: CalculatedValue,
        val shieldTotalsAssumptions: List<CalcAssumption> = emptyList(),
        val calcWarnings: List<CalcWarning> = emptyList()
    ) : GroupScreenState()

    data class Error(val message: String) : GroupScreenState()
}

private fun buildWarningsFromGroups(groups: List<CircuitGroup>): List<CalcWarning> {
    val warnings = mutableListOf<CalcWarning>()

    groups.forEach { g ->
        g.devices.forEach { d ->
            // Ровно тот же триггер, что был в Log.w в buildReportData()
            if (d.deviceType == DeviceType.LIGHTING && (d.power ?: 0) >= 1000) {
                warnings += CalcWarning(
                    severity = CalcWarning.Severity.WARNING,
                    scope = "device:${d.id}",
                    title = "Аномальная мощность освещения",
                    message = "Освещение '${d.name}': ${(d.power ?: 0)}W"
                )
            }
        }
    }

    return warnings
}

/**
 * Формирование данных отчёта для PDF/превью.
 * Поддерживает оба режима:
 *  - THREE: донат распределения по фазам, headline A/B/C
 *  - SINGLE: донат загрузки вводного, headline только A
 */
fun ExplicationViewModel.buildReportData(): Pair<ReportMeta, List<ReportPhase>>? {
    val plan = userPlanRepository.planFlow.value
    if (!plan.capabilities.pdfExport) return null

    val s = uiState.value as? GroupScreenState.Success ?: return null
    val date =
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(System.currentTimeMillis())

    val mode = phaseMode.value
    val perPhase = phaseCurrents(s.groups)

    val headlineCurrents: Map<String, Double> = when (mode) {
        PhaseMode.THREE -> mapOf(
            "A" to perPhase.getOrZero(Phase.A),
            "B" to perPhase.getOrZero(Phase.B),
            "C" to perPhase.getOrZero(Phase.C)
        )

        PhaseMode.SINGLE -> mapOf(
            "A" to perPhase.getOrZero(Phase.A)
        )
    }

    val donut: DonutModel = when (mode) {
        PhaseMode.THREE -> DonutModel.PhaseDistribution(valuesA = perPhase)
        PhaseMode.SINGLE -> {
            val usedA = perPhase.getOrZero(Phase.A)
            val limitA = s.incomer.mcbRating.toDouble()
            DonutModel.IncomerLoad(usedA = usedA, limitA = limitA)
        }
    }

    val meta = ReportMeta(
        date = date,
        incomerLabel = with(s.incomer) {
            buildString {
                append(kind.name)
                append(", ")
                append("${poles}P, ")
                append("${mcbRating}A ${mcbCurve}, Icn ${icn}")
                rcdType?.let { append(", RCD ${it} ${rcdSensitivityMa ?: 30}mA") }
            }
        },
        headlineCurrents = headlineCurrents,
        donut = donut,
        totalGroups = s.totalGroups
    )

    val phases = s.groups
        .groupBy { it.phase }
        .toSortedMap(compareBy { it.name })
        .map { (phase, groups) ->
            ReportPhase(
                name = "Фаза ${phase.name}",
                groups = groups.sortedBy { it.groupNumber }.map { g ->
                    ReportGroup(
                        title = "Группа #${g.groupNumber} — ${g.roomName}",
                        switchLabel = GroupMetaFormatter.buildSwitchLabel(g),
                        cableLabel = GroupMetaFormatter.buildCableLabel(g),
                        devices = g.devices.map { d ->
                            val normalized = PowerCurrentNormalizer.ensurePAndI(
                                powerW = d.power.takeIf { it > 0 },
                                currentA = d.calculateCurrent().takeIf { it > 0.0 },
                                voltage = d.voltage,
                                powerFactor = d.powerFactor
                            )
                            val powerW = normalized.powerW
                            val currentA = normalized.currentA
                            ReportDevice(
                                name = d.name,
                                powerW = powerW,
                                currentA = currentA
                            )
                        }
                    )
                }
            )
        }

    return meta to phases
}