package ru.mugalimov.volthome.ui.viewmodel

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
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.formatter.GroupMetaFormatter
import ru.mugalimov.volthome.domain.model.CalcAssumption
import ru.mugalimov.volthome.domain.model.CalcStep
import ru.mugalimov.volthome.domain.model.CalcWarning
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceCalcBreakdown
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.report.DonutModel
import ru.mugalimov.volthome.domain.model.report.ReportDevice
import ru.mugalimov.volthome.domain.model.report.ReportGroup
import ru.mugalimov.volthome.domain.model.report.ReportMeta
import ru.mugalimov.volthome.domain.model.report.ReportPhase
import ru.mugalimov.volthome.domain.model.report.professional.ProfessionalSections
import ru.mugalimov.volthome.domain.use_case.CalculateDeviceBreakdownUseCase
import ru.mugalimov.volthome.domain.use_case.CalculateGroupBreakdownUseCase
import ru.mugalimov.volthome.domain.use_case.CalculateShieldOverviewUseCase
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import ru.mugalimov.volthome.domain.use_case.getOrZero
import ru.mugalimov.volthome.domain.use_case.phaseCurrents
import ru.mugalimov.volthome.domain.use_case.report.BuildProfessionalSectionsUseCase
import ru.mugalimov.volthome.domain.util.PowerCurrentNormalizer
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcBlockUi
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetPayload
import ru.mugalimov.volthome.ui.viewmodel.explication.InfoSheetPayloadFactory
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExplicationViewModel @Inject constructor(
    private val repo: ExplicationRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: PreferencesRepository,
    private val deviceRepository: DeviceRepository,
    private val calculateShieldOverviewUseCase: CalculateShieldOverviewUseCase,
    private val calculateDeviceBreakdownUseCase: CalculateDeviceBreakdownUseCase,
    private val calculateGroupBreakdownUseCase: CalculateGroupBreakdownUseCase,
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

    // --- BottomSheet payload (единый для подсказок) ---
    private val _infoSheetPayload = MutableStateFlow<InfoSheetPayload?>(null)
    val infoSheetPayload: StateFlow<InfoSheetPayload?> = _infoSheetPayload.asStateFlow()

    fun openInfoSheet(payload: InfoSheetPayload) {
        _infoSheetPayload.value = payload
    }

    fun closeInfoSheet() {
        _infoSheetPayload.value = null
    }

    fun onGroupPowerClick(group: CircuitGroup) {
        val plan = userPlanRepository.planFlow.value
        val isPro = plan.capabilities.professionalReportSections

        val breakdown = calculateGroupBreakdownUseCase.execute(group)

        openInfoSheet(
            InfoSheetPayload(
                title = "Мощность группы",
                currentValueText = "%.2f кВт".format(breakdown.installedPower.value / 1000.0),
                calcBlocks = if (isPro) breakdown.installedPower.steps.toCalcBlocksUi() else emptyList(),
                normRefs = emptyList()
            )
        )
    }

    fun onGroupCurrentClick(group: CircuitGroup) {
        val plan = userPlanRepository.planFlow.value
        val isPro = plan.capabilities.professionalReportSections

        val breakdown = calculateGroupBreakdownUseCase.execute(group)

        openInfoSheet(
            InfoSheetPayload(
                title = "Расчётный ток",
                currentValueText = "%.2f А".format(breakdown.calculatedCurrent.value),
                calcBlocks = if (isPro) breakdown.calculatedCurrent.steps.toCalcBlocksUi() else emptyList(),
                normRefs = emptyList()
            )
        )
    }
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
    fun onDeviceClick(deviceId: Long) {
        viewModelScope.launch {
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

    fun onInstalledPowerClick(calculated: CalculatedValue) {
        val plan = userPlanRepository.planFlow.value

        openInfoSheet(
            InfoSheetPayload(
                title = "Установленная мощность",
                currentValueText = "%.1f кВт".format(calculated.value / 1000.0),
                calcBlocks = calculated.steps.toCalcBlocksUi(),
                normRefs = if (plan.capabilities.professionalReportSections) {
                    calculated.normRefs.map { it.toString() } // пока так, т.к. NormRef модель ты не прислал
                } else emptyList()
            )
        )
    }

    fun onCalculatedPowerClick(calculated: CalculatedValue) {
        val plan = userPlanRepository.planFlow.value

        openInfoSheet(
            InfoSheetPayload(
                title = "Расчётная нагрузка",
                currentValueText = "%.1f кВт".format(calculated.value / 1000.0),
                calcBlocks = calculated.steps.toCalcBlocksUi(),
                normRefs = if (plan.capabilities.professionalReportSections) {
                    calculated.normRefs.map { it.toString() }
                } else emptyList()
            )
        )
    }

    private fun List<CalcStep>.toCalcBlocksUi(): List<CalcBlockUi> {
        return map { step ->
            CalcBlockUi(
                formulaText = step.formula,
                substitutionLines = step.inputs.map { input ->
                    // "Σ Pпаспорт = 1234 Вт"
                    "${input.name} = ${fmtNumber(input.value)} ${input.unit}"
                },
                resultText = "${fmtNumber(step.output.value)} ${step.output.unit}"
            )
        }
    }

    private fun fmtNumber(v: Double): String {
        // простая нормальная печать без запятых
        val s = String.format(Locale.US, "%.2f", v)
        return s.trimEnd('0').trimEnd('.')
    }

    fun onIncomerFieldClick(field: InfoSheetPayloadFactory.IncomerField, incomer: IncomerSpec, hasGroupRcds: Boolean) {
        val payload = InfoSheetPayloadFactory.incomerField(
            field = field,
            incomer = incomer,
            phaseMode = phaseMode.value,
            hasGroupRcds = hasGroupRcds
        )
        openInfoSheet(payload)
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

                        // ✅ decision log распределения фаз → в репозиторий (in-memory)
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

                        val calcWarningsFromGroups = if (isProReport) {
                            buildWarningsFromGroups(groups)
                        } else {
                            emptyList()
                        }

                        val installedPower: CalculatedValue = if (isProReport) {
                            totals.installedPowerW
                        } else {
                            totals.installedPowerW.copy(
                                steps = emptyList(),
                                assumptions = emptyList(),
                                warnings = emptyList(),
                                normRefs = emptyList()
                            )
                        }

                        val calculatedPower: CalculatedValue = if (isProReport) {
                            totals.calculatedPowerW
                        } else {
                            totals.calculatedPowerW.copy(
                                steps = emptyList(),
                                assumptions = emptyList(),
                                warnings = emptyList(),
                                normRefs = emptyList()
                            )
                        }

                        val shieldTotalsAssumptions: List<CalcAssumption> =
                            if (isProReport) calculatedPower.assumptions else emptyList()

                        // === ProfessionalSections: собираем из фактов (без чтения uiState) ===

                        val proAssumptions: List<CalcAssumption> =
                            if (isProReport) {
                                buildList {
                                    addAll(installedPower.assumptions)
                                    addAll(calculatedPower.assumptions)
                                    addAll(shieldTotalsAssumptions)
                                }.distinctBy { it.toString() }
                            } else emptyList()

                        val proWarnings: List<CalcWarning> =
                            if (isProReport) {
                                buildList {
                                    addAll(calcWarningsFromGroups)
                                    addAll(installedPower.warnings)
                                    addAll(calculatedPower.warnings)
                                }.distinctBy { "${it.severity}|${it.scope}|${it.title}|${it.message}" }
                            } else emptyList()

                        val (meta, phases) = buildReportDataFrom(
                            groups = groups,
                            incomer = incomer,
                            totalGroups = totalGroups,
                            mode = mode
                        )

                        val professionalSections: ProfessionalSections? =
                            if (isProReport) {
                                BuildProfessionalSectionsUseCase().execute(
                                    BuildProfessionalSectionsUseCase.Params(
                                        phaseMode = mode,
                                        meta = meta,
                                        phases = phases,
                                        distributionDecisions = res.distributionDecisions,
                                        calcWarnings = proWarnings,
                                        assumptions = proAssumptions
                                    )
                                )
                            } else null

                        _uiState.value = GroupScreenState.Success(
                            groups = groups,
                            totalGroups = totalGroups,
                            totalCurrent = totalCurrent,
                            incomer = incomer,
                            hasGroupRcds = hasGroupRcds,
                            installedPowerW = installedPower,
                            calculatedPowerW = calculatedPower,
                            shieldTotalsAssumptions = shieldTotalsAssumptions,
                            calcWarnings = calcWarningsFromGroups,
                            professionalSections = professionalSections
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
        val calcWarnings: List<CalcWarning> = emptyList(),
        val professionalSections: ProfessionalSections? = null
    ) : GroupScreenState()

    data class Error(val message: String) : GroupScreenState()
}

private fun buildWarningsFromGroups(groups: List<CircuitGroup>): List<CalcWarning> {
    val warnings = mutableListOf<CalcWarning>()

    groups.forEach { g ->
        g.devices.forEach { d ->
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

private fun buildReportDataFrom(
    groups: List<CircuitGroup>,
    incomer: IncomerSpec,
    totalGroups: Int,
    mode: PhaseMode
): Pair<ReportMeta, List<ReportPhase>> {
    val date =
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(System.currentTimeMillis())

    val perPhase = phaseCurrents(groups)

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
            val limitA = incomer.mcbRating.toDouble()
            DonutModel.IncomerLoad(usedA = usedA, limitA = limitA)
        }
    }

    val meta = ReportMeta(
        date = date,
        incomerLabel = with(incomer) {
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
        totalGroups = totalGroups
    )

    val phases = groups
        .groupBy { it.phase }
        .toSortedMap(compareBy { it.name })
        .map { (phase, phaseGroups) ->
            ReportPhase(
                name = "Фаза ${phase.name}",
                groups = phaseGroups.sortedBy { it.groupNumber }.map { g ->
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
                            ReportDevice(
                                name = d.name,
                                powerW = normalized.powerW,
                                currentA = normalized.currentA
                            )
                        }
                    )
                }
            )
        }

    return meta to phases
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

    return buildReportDataFrom(
        groups = s.groups,
        incomer = s.incomer,
        totalGroups = s.totalGroups,
        mode = phaseMode.value
    )
}