package ru.mugalimov.volthome.ui.screens.project_wizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.domain.model.create.RoomCreateRequest
import ru.mugalimov.volthome.domain.model.projectwizard.CreateProjectFromTemplateRequest
import ru.mugalimov.volthome.domain.model.projectwizard.CreatedProjectSummary
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectTemplate
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectTemplateRoom
import ru.mugalimov.volthome.domain.model.provider.ProjectTemplateCatalog
import ru.mugalimov.volthome.domain.use_case.CreateProjectFromTemplateUseCase
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.ui.paywall.PaywallBus

data class WizardRoomUi(
    val template: ProjectTemplateRoom,
    val count: Int,
    val deviceCounts: Map<Long, Int>
)

data class ProjectWizardUiState(
    val isLoading: Boolean = true,
    val step: Int = 0,
    val templates: List<ProjectTemplate> = emptyList(),
    val existingProjectsCount: Int = 0,
    val selectedTemplate: ProjectTemplate? = null,
    val projectName: String = "",
    val phaseMode: PhaseMode = PhaseMode.SINGLE,
    val inputPowerKnown: Boolean = false,
    val inputPowerText: String = "",
    val catalog: Map<Long, DefaultDevice> = emptyMap(),
    val rooms: List<WizardRoomUi> = emptyList(),
    val isCreating: Boolean = false,
    val error: String? = null,
    val result: CreatedProjectSummary? = null
) {
    val isBlankTemplate: Boolean get() = selectedTemplate?.rooms.isNullOrEmpty()
    val canContinue: Boolean
        get() = when (step) {
            0 -> selectedTemplate != null
            1 -> projectName.isNotBlank() && inputPowerError == null
            2 -> isBlankTemplate || rooms.any { it.count > 0 }
            3 -> !isCreating &&
                incompatibleThreePhaseDevices.isEmpty() &&
                (isBlankTemplate || devicesCount > 0)
            else -> false
        }

    val roomsCount: Int get() = rooms.sumOf { it.count }
    val devicesCount: Int get() = rooms.sumOf { room -> room.count * room.deviceCounts.values.sum() }

    val estimatedInstalledPowerW: Double
        get() = rooms.sumOf { room ->
            room.count * room.deviceCounts.entries.sumOf { (deviceId, count) ->
                (catalog[deviceId]?.power ?: 0) * count.toDouble()
            }
        }

    val estimatedCalculatedPowerW: Double
        get() = rooms.sumOf { room ->
            room.count * room.deviceCounts.entries.sumOf { (deviceId, count) ->
                val device = catalog[deviceId]
                (device?.power ?: 0) * (device?.demandRatio ?: 0.0) * count
            }
        }

    val inputPowerValueKw: Double?
        get() = inputPowerText.replace(',', '.').toDoubleOrNull()

    val inputPowerError: String?
        get() = when {
            !inputPowerKnown -> null
            inputPowerText.isBlank() -> "Укажите доступную мощность"
            inputPowerValueKw == null -> "Введите число, например 15,0"
            inputPowerValueKw!! < 0.5 -> "Минимальное значение — 0,5 кВт"
            inputPowerValueKw!! > 1000.0 -> "Максимальное значение — 1000 кВт"
            else -> null
        }

    val inputPowerWarning: String?
        get() {
            val available = inputPowerValueKw ?: return null
            val calculated = estimatedCalculatedPowerW / 1000.0
            return when {
                calculated > available ->
                    "Оценочная нагрузка ${formatPower(calculated)} кВт выше доступных ${formatPower(available)} кВт"
                calculated >= available * 0.8 ->
                    "Оценочная нагрузка использует более 80% доступной мощности"
                else -> null
            }
        }

    val incompatibleThreePhaseDevices: List<String>
        get() = if (phaseMode == PhaseMode.THREE) emptyList() else rooms.flatMap { room ->
            room.deviceCounts.filterValues { it > 0 }.keys.mapNotNull { id ->
                catalog[id]?.takeIf { it.voltage.type == VoltageType.AC_3PHASE }?.name
            }
        }.distinct()
}

@HiltViewModel
class ProjectWizardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val templateCatalog: ProjectTemplateCatalog,
    private val projectsRepository: ProjectsRepository,
    private val createProjectFromTemplate: CreateProjectFromTemplateUseCase,
    private val paywallBus: PaywallBus
) : ViewModel() {
    private val _state = MutableStateFlow(ProjectWizardUiState())
    val state: StateFlow<ProjectWizardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val templates = templateCatalog.templates()
            val catalog = templateCatalog.devices().associateBy(DefaultDevice::id)
            val savedTemplateId = savedStateHandle.get<String>(KEY_TEMPLATE_ID)
            val template = templates.firstOrNull { it.id == savedTemplateId } ?: templates.first()
            val existing = projectsRepository.listProjects().first()
            val savedPhase = savedStateHandle.get<String>(KEY_PHASE)
                ?.let { runCatching { PhaseMode.valueOf(it) }.getOrNull() }
                ?: template.recommendedPhaseMode
            val roomCounts = decodeMap(savedStateHandle[KEY_ROOM_COUNTS])
            val deviceCounts = decodeDeviceMap(savedStateHandle[KEY_DEVICE_COUNTS])
            _state.value = ProjectWizardUiState(
                isLoading = false,
                step = savedStateHandle[KEY_STEP] ?: 0,
                templates = templates,
                existingProjectsCount = existing.size,
                selectedTemplate = template,
                projectName = savedStateHandle[KEY_PROJECT_NAME] ?: nextProjectName(existing.map { it.name }),
                phaseMode = savedPhase,
                inputPowerKnown = savedStateHandle[KEY_INPUT_KNOWN] ?: false,
                inputPowerText = savedStateHandle[KEY_INPUT_POWER] ?: "",
                catalog = catalog,
                rooms = template.rooms.map { room ->
                    WizardRoomUi(
                        template = room,
                        count = roomCounts[room.key] ?: room.defaultCount,
                        deviceCounts = room.devices.associate { device ->
                            device.deviceId to (deviceCounts[room.key to device.deviceId]
                                ?: if (device.enabled) device.count else 0)
                        }
                    )
                }
            )
        }
    }

    fun selectTemplate(id: String) {
        val template = _state.value.templates.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                selectedTemplate = template,
                phaseMode = template.recommendedPhaseMode,
                rooms = template.rooms.map { room ->
                    WizardRoomUi(
                        template = room,
                        count = room.defaultCount,
                        deviceCounts = room.devices.associate { d -> d.deviceId to if (d.enabled) d.count else 0 }
                    )
                },
                error = null
            )
        }
        persistDraft()
    }

    fun setProjectName(value: String) {
        _state.update { it.copy(projectName = value.take(60), error = null) }
        persistDraft()
    }

    fun setPhaseMode(mode: PhaseMode) {
        _state.update { it.copy(phaseMode = mode, error = null) }
        persistDraft()
    }

    fun setInputPowerKnown(known: Boolean) {
        _state.update { it.copy(inputPowerKnown = known, inputPowerText = if (known) it.inputPowerText else "") }
        persistDraft()
    }

    fun setInputPower(value: String) {
        val normalized = value.replace('.', ',')
        val filtered = buildString {
            var separatorSeen = false
            normalized.forEach { char ->
                when {
                    char.isDigit() -> append(char)
                    char == ',' && !separatorSeen -> {
                        append(char)
                        separatorSeen = true
                    }
                }
            }
        }.take(8)
        _state.update { it.copy(inputPowerText = filtered, error = null) }
        persistDraft()
    }

    fun changeRoomCount(key: String, delta: Int) {
        _state.update { state ->
            state.copy(
                rooms = state.rooms.map { room ->
                    if (room.template.key == key) room.copy(count = (room.count + delta).coerceIn(0, 12)) else room
                }
            )
        }
        persistDraft()
    }

    fun changeDeviceCount(roomKey: String, deviceId: Long, delta: Int) {
        _state.update { state ->
            state.copy(
                rooms = state.rooms.map { room ->
                    if (room.template.key != roomKey) room else room.copy(
                        deviceCounts = room.deviceCounts.toMutableMap().apply {
                            this[deviceId] = ((this[deviceId] ?: 0) + delta).coerceIn(0, 30)
                        }
                    )
                }
            )
        }
        persistDraft()
    }

    fun next() {
        if (!_state.value.canContinue) return
        _state.update { it.copy(step = (it.step + 1).coerceAtMost(3), error = null) }
        persistDraft()
    }

    fun requestProjectsUpgrade() {
        paywallBus.request(ProFeature.PROJECTS_LIMIT)
    }

    fun back(): Boolean {
        if (_state.value.result != null) return false
        if (_state.value.step == 0) return false
        _state.update { it.copy(step = it.step - 1, error = null) }
        persistDraft()
        return true
    }

    fun createProject() {
        val snapshot = _state.value
        if (!snapshot.canContinue || snapshot.selectedTemplate == null) return
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null) }
            val request = CreateProjectFromTemplateRequest(
                name = snapshot.projectName,
                objectType = snapshot.selectedTemplate.objectType,
                phaseMode = snapshot.phaseMode,
                inputPowerKw = snapshot.inputPowerValueKw.takeIf { snapshot.inputPowerKnown },
                templateId = snapshot.selectedTemplate.id,
                templateVersion = snapshot.selectedTemplate.version,
                rooms = buildRequests(snapshot)
            )
            when (val outcome = createProjectFromTemplate(request)) {
                is CreateProjectFromTemplateUseCase.Outcome.Success -> {
                    clearDraft()
                    _state.update { it.copy(isCreating = false, result = outcome.summary) }
                }
                is CreateProjectFromTemplateUseCase.Outcome.LimitReached -> {
                    _state.update { it.copy(isCreating = false, error = "Достигнут лимит проектов Free") }
                    paywallBus.request(ProFeature.PROJECTS_LIMIT)
                }
                is CreateProjectFromTemplateUseCase.Outcome.Failure -> {
                    _state.update { it.copy(isCreating = false, error = outcome.message) }
                }
            }
        }
    }

    private fun buildRequests(state: ProjectWizardUiState): List<RoomCreateRequest> =
        state.rooms.flatMap { room ->
            (1..room.count).map { index ->
                val name = if (room.count == 1) room.template.title else "${room.template.title} $index"
                RoomCreateRequest(
                    name = name,
                    roomType = room.template.roomType,
                    devices = room.deviceCounts.mapNotNull { (deviceId, count) ->
                        val device = state.catalog[deviceId] ?: return@mapNotNull null
                        if (count <= 0) return@mapNotNull null
                        DeviceCreateRequest(
                            title = device.name,
                            type = device.deviceType,
                            count = count,
                            ratedPowerW = device.power,
                            powerFactor = device.powerFactor,
                            demandRatio = device.demandRatio,
                            voltage = device.voltage,
                            hasMotor = device.hasMotor,
                            requiresDedicatedCircuit = device.requiresDedicatedCircuit,
                            requiresSocketConnection = device.requiresSocketConnection
                        )
                    }
                )
            }
        }

    private fun persistDraft() {
        val state = _state.value
        savedStateHandle[KEY_STEP] = state.step
        savedStateHandle[KEY_TEMPLATE_ID] = state.selectedTemplate?.id
        savedStateHandle[KEY_PROJECT_NAME] = state.projectName
        savedStateHandle[KEY_PHASE] = state.phaseMode.name
        savedStateHandle[KEY_INPUT_KNOWN] = state.inputPowerKnown
        savedStateHandle[KEY_INPUT_POWER] = state.inputPowerText
        savedStateHandle[KEY_ROOM_COUNTS] = ArrayList(state.rooms.map { "${it.template.key}=${it.count}" })
        savedStateHandle[KEY_DEVICE_COUNTS] = ArrayList(state.rooms.flatMap { room ->
            room.deviceCounts.map { (id, count) -> "${room.template.key}:$id=$count" }
        })
    }

    private fun clearDraft() {
        listOf(KEY_STEP, KEY_TEMPLATE_ID, KEY_PROJECT_NAME, KEY_PHASE, KEY_INPUT_KNOWN, KEY_INPUT_POWER,
            KEY_ROOM_COUNTS, KEY_DEVICE_COUNTS).forEach { key -> savedStateHandle.remove<Any>(key) }
    }

    private fun decodeMap(values: ArrayList<String>?): Map<String, Int> = values.orEmpty().mapNotNull { value ->
        val parts = value.split('=', limit = 2)
        parts.takeIf { it.size == 2 }?.let { it[0] to it[1].toIntOrNull() }?.takeIf { it.second != null }
            ?.let { it.first to it.second!! }
    }.toMap()

    private fun decodeDeviceMap(values: ArrayList<String>?): Map<Pair<String, Long>, Int> =
        values.orEmpty().mapNotNull { value ->
            val pair = value.split('=', limit = 2)
            val key = pair.getOrNull(0)?.split(':', limit = 2)
            val count = pair.getOrNull(1)?.toIntOrNull()
            if (key?.size == 2 && count != null) {
                val id = key[1].toLongOrNull() ?: return@mapNotNull null
                (key[0] to id) to count
            } else null
        }.toMap()

    private fun nextProjectName(names: List<String>): String {
        val expression = Regex("""^Проект №(\d+)$""")
        val max = names.mapNotNull { expression.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "Проект №${max + 1}"
    }

    private companion object {
        const val KEY_STEP = "wizard.step"
        const val KEY_TEMPLATE_ID = "wizard.template"
        const val KEY_PROJECT_NAME = "wizard.project_name"
        const val KEY_PHASE = "wizard.phase"
        const val KEY_INPUT_KNOWN = "wizard.input_known"
        const val KEY_INPUT_POWER = "wizard.input_power"
        const val KEY_ROOM_COUNTS = "wizard.room_counts"
        const val KEY_DEVICE_COUNTS = "wizard.device_counts"
    }
}

private fun formatPower(value: Double): String =
    String.format(java.util.Locale("ru", "RU"), "%.1f", value)
