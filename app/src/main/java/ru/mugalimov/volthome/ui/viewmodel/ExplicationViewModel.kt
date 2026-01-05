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

@HiltViewModel
class ExplicationViewModel @Inject constructor(
    private val repo: ExplicationRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: PreferencesRepository,
    private val deviceRepository: DeviceRepository,
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    private val userPlanRepository: UserPlanRepository,
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
                // Пересчитать (при необходимости можно задебаунсить)
                recalcAndSaveGroups()
            }
        }
    }

    fun onExportPdfClick() {
        val plan = userPlanRepository.planFlow.value
        if (!plan.isPro) {
            paywallBus.request(ProFeature.PDF_EXPORT)
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
            val dev = deviceRepository.getDeviceById(deviceId.toInt())
            _selectedDevice.value = dev // может быть null, если не нашли
        }
    }

    fun clearSelected() {
        _selectedDevice.value = null
    }

    fun recalcAndSaveGroups() {
        viewModelScope.launch(dispatchers) {
            _uiState.value = GroupScreenState.Loading
            try {
                // 1) Получаем текущий режим (SINGLE / THREE)
                val mode: PhaseMode = preferencesRepository.phaseMode.first()

                // 2) Считаем группы с учётом режима
                val calc = groupCalculatorFactory.create()
                when (val res = calc.calculateGroups(mode)) { // ← ВАЖНО: передаём mode
                    is GroupingResult.Error -> {
                        _uiState.value = GroupScreenState.Error(res.message)
                    }

                    is GroupingResult.Success -> {
                        val groups: List<CircuitGroup> = res.system.groups

                        // 3) Сохраняем в БД (транзакционно, как и раньше)
                        repo.replaceAllGroupsTransactional(groups)

                        // 4) Подготавливаем метаданные для UI
                        val totalGroups = groups.size
                        val totalCurrent = groups.sumOf { it.nominalCurrent }
                        val hasGroupRcds = groups.any { it.rcdRequired }

                        val incomer = IncomerSelector().select(
                            IncomerSelector.Params(
                                groups = groups,
                                preferRcbo = false,  // можно вынести в настройки
                                hasGroupRcds = hasGroupRcds,
                                voltageTypeOverride = when (mode) {
                                    PhaseMode.SINGLE -> VoltageType.AC_1PHASE
                                    PhaseMode.THREE -> VoltageType.AC_3PHASE
                                }
                            )
                        )

                        // 5) Отдаём в UI итог
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
        val incomer: IncomerSpec,
        val hasGroupRcds: Boolean
    ) : GroupScreenState()

    data class Error(val message: String) : GroupScreenState()
}

/**
 * Формирование данных отчёта для PDF/превью.
 * Поддерживает оба режима:
 *  - THREE: донат распределения по фазам, headline A/B/C
 *  - SINGLE: донат загрузки вводного, headline только A
 */
fun ExplicationViewModel.buildReportData(): Pair<ReportMeta, List<ReportPhase>>? {
    val s = uiState.value as? GroupScreenState.Success ?: return null
    val date =
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(System.currentTimeMillis())

    val mode = phaseMode.value
    val perPhase = phaseCurrents(s.groups)

    // Заголовочные токи (что показываем в шапке отчёта)
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

    // Донат: распределение по фазам (3-ф) или загрузка вводного (1-ф)
    val donut: DonutModel = when (mode) {
        PhaseMode.THREE -> DonutModel.PhaseDistribution(valuesA = perPhase)
        PhaseMode.SINGLE -> {
            val usedA = perPhase.getOrZero(Phase.A)
            // номинал берём из вводного автомата
            val limitA = s.incomer.mcbRating.toDouble()
            DonutModel.IncomerLoad(usedA = usedA, limitA = limitA)
        }
    }

    val meta = ReportMeta(
        date = date,
        incomerLabel = with(s.incomer) {
            // Короткое описание вводного аппарата
            buildString {
                append(kind.name)    // RCBO/MCB_ONLY/MCB_PLUS_RCD
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
                        // Метки аппарата/кабеля — формируем на этапе маппинга
                        switchLabel = GroupMetaFormatter.buildSwitchLabel(g),
                        cableLabel = GroupMetaFormatter.buildCableLabel(g),
                        devices = g.devices.map { d ->
                            // Двусторонняя нормализация P/I для отчёта
                            val (powerW, currentA) = PowerCurrentNormalizer.ensurePAndI(
                                powerW = d.power.takeIf { it > 0 },
                                currentA = d.calculateCurrent().takeIf { it > 0.0 },
                                voltage = d.voltage,
                                powerFactor = d.powerFactor
                            )
                            // Диагностика аномалий (не влияет на UI/PDF)
                            if (d.deviceType == DeviceType.LIGHTING && (powerW ?: 0) >= 1000) {
                                Log.w(
                                    "ReportNormalizer",
                                    "Lighting device anomalous power: ${d.name} = ${powerW}W"
                                )
                            }
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