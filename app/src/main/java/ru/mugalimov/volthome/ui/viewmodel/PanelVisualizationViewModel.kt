package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.CableCalculationRepository
import ru.mugalimov.volthome.data.repository.PanelEquipmentRepository
import ru.mugalimov.volthome.data.repository.PanelLayoutRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.data.repository.observeResolved
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryApparatusKind
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryApparatusProduct
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryElectricalConnection
import ru.mugalimov.volthome.domain.model.catalog.ProductStatus
import ru.mugalimov.volthome.domain.model.catalog.SelectedApparatusSnapshot
import ru.mugalimov.volthome.domain.model.incomer.IncomerAssessment
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutRailSnapshot
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutSnapshot
import ru.mugalimov.volthome.domain.model.panel.PanelMoveDirection
import ru.mugalimov.volthome.domain.model.panel.PanelVisualization
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.model.pricing.ProtectionCostEstimate
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec
import ru.mugalimov.volthome.domain.model.pricing.PanelEquipmentEstimate
import ru.mugalimov.volthome.domain.model.project.ProjectEngineeringSnapshot
import ru.mugalimov.volthome.domain.model.provider.ApparatusCatalogProvider
import ru.mugalimov.volthome.domain.use_case.ApparatusCandidate
import ru.mugalimov.volthome.domain.use_case.FindCompatibleApparatusUseCase
import ru.mugalimov.volthome.domain.use_case.GeneratePanelVisualizationUseCase
import ru.mugalimov.volthome.domain.use_case.IncomerSelector
import ru.mugalimov.volthome.domain.use_case.PanelLayoutEngine
import ru.mugalimov.volthome.domain.use_case.BuildProjectEngineeringSnapshotUseCase
import ru.mugalimov.volthome.domain.use_case.SelectApparatusProductUseCase
import javax.inject.Inject

sealed interface PanelVisualizationUiState {
    data object Loading : PanelVisualizationUiState

    data class Content(
        val panel: PanelVisualization,
        val incomerAssessment: IncomerAssessment,
        val protectionCostEstimate: ProtectionCostEstimate,
        val auxiliaryCostEstimate: MoneyRange,
        val totalEquipmentCost: MoneyRange,
        val equipmentEstimate: PanelEquipmentEstimate,
        val engineeringSnapshot: ProjectEngineeringSnapshot,
        val selections: Map<String, SelectedApparatusSnapshot>,
        val layoutSnapshot: PanelLayoutSnapshot,
        val auxiliaryProducts: List<AuxiliaryApparatusProduct>,
        val apparatusCatalogVersion: String,
        val apparatusCatalogPublishedAt: String,
        val isEditing: Boolean,
        val hasUnsavedChanges: Boolean,
        val calculatedStructureChanged: Boolean
    ) : PanelVisualizationUiState {
        val customModuleCount: Int
            get() = layoutSnapshot.customModules.size
    }

    data object Empty : PanelVisualizationUiState
    data class Error(val message: String) : PanelVisualizationUiState
}

sealed interface ApparatusSelectionOperationState {
    data object Idle : ApparatusSelectionOperationState
    data class Saving(val slotId: String) : ApparatusSelectionOperationState
    data class Success(val message: String) : ApparatusSelectionOperationState
    data class Error(val message: String) : ApparatusSelectionOperationState
}

private data class PanelEditorSession(
    val projectId: String,
    val original: PanelLayoutSnapshot,
    val draft: PanelLayoutSnapshot,
    val calculatedStructureChanged: Boolean
)

private data class PanelElectricalContext(
    val phaseMode: PhaseMode,
    val availablePowerKw: Double?,
    val devices: List<Device>
)

/**
 * Расчётный щит остаётся read-only. Редактор меняет только отдельный снимок
 * физической DIN-компоновки и дополнительные пользовательские аппараты.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PanelVisualizationViewModel @Inject constructor(
    private val activeProjectDataStore: ActiveProjectDataStore,
    private val explicationRepository: ExplicationRepository,
    private val cableCalculationRepository: CableCalculationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val projectSetupRepository: ProjectSetupRepository,
    private val panelEquipmentRepository: PanelEquipmentRepository,
    private val panelLayoutRepository: PanelLayoutRepository,
    private val apparatusCatalogProvider: ApparatusCatalogProvider,
    private val incomerSelector: IncomerSelector,
    private val generatePanelVisualizationUseCase: GeneratePanelVisualizationUseCase,
    private val buildProjectEngineeringSnapshot: BuildProjectEngineeringSnapshotUseCase,
    private val findCompatibleApparatusUseCase: FindCompatibleApparatusUseCase,
    private val selectApparatusProductUseCase: SelectApparatusProductUseCase,
    private val panelLayoutEngine: PanelLayoutEngine
) : ViewModel() {

    private val _selectionOperationState =
        MutableStateFlow<ApparatusSelectionOperationState>(ApparatusSelectionOperationState.Idle)
    val selectionOperationState: StateFlow<ApparatusSelectionOperationState> =
        _selectionOperationState.asStateFlow()

    private val editorSession = MutableStateFlow<PanelEditorSession?>(null)

    val uiState: StateFlow<PanelVisualizationUiState> =
        activeProjectDataStore.activeProjectId
            .distinctUntilChanged()
            .flatMapLatest { projectId ->
                val normalizedProjectId = projectId.orEmpty().trim()
                if (normalizedProjectId.isBlank()) {
                    flowOf(PanelVisualizationUiState.Empty)
                } else {
                    val groupsAndCables = combine(
                        explicationRepository.observeAllGroupByProject(normalizedProjectId),
                        cableCalculationRepository.observeCalculations(normalizedProjectId)
                    ) { groups, cableCalculations -> groups to cableCalculations }
                    val legacyFallbackMode = preferencesRepository.phaseMode.first()
                    val electricalContext = combine(
                        projectSetupRepository.observeResolved(normalizedProjectId, legacyFallbackMode),
                        explicationRepository.observeAllDevicesByProject(normalizedProjectId)
                    ) { setup, devices ->
                        PanelElectricalContext(
                            phaseMode = setup.phaseMode,
                            availablePowerKw = setup.inputPowerKw,
                            devices = devices
                        )
                    }
                    combine(
                        groupsAndCables,
                        electricalContext,
                        panelEquipmentRepository.observeSelections(normalizedProjectId),
                        panelLayoutRepository.observe(normalizedProjectId),
                        editorSession
                    ) { (groups, cableCalculations), context, selections, savedLayout, currentEditor ->
                        if (groups.isEmpty()) {
                            PanelVisualizationUiState.Empty
                        } else {
                            val assignedDeviceIds = groups
                                .asSequence()
                                .flatMap { it.devices.asSequence() }
                                .map { it.id }
                                .filter { it > 0L }
                                .toSet()
                            val unassignedDeviceCount = context.devices.count { device ->
                                device.id > 0L && device.id !in assignedDeviceIds
                            }
                            val incomerAssessment = incomerSelector.assess(
                                IncomerSelector.Params(
                                    groups = groups,
                                    hasGroupRcds = groups.any { it.rcdRequired },
                                    voltageTypeOverride = when (context.phaseMode) {
                                        PhaseMode.SINGLE -> VoltageType.AC_1PHASE
                                        PhaseMode.THREE -> VoltageType.AC_3PHASE
                                    },
                                    availablePowerKw = context.availablePowerKw,
                                    unassignedDeviceCount = unassignedDeviceCount
                                )
                            )
                            val incomer = incomerAssessment.spec
                            val calculatedPanel = generatePanelVisualizationUseCase(
                                incomer = incomer,
                                groups = groups,
                                cableCalculations = cableCalculations
                            )
                            val activeEditor = currentEditor
                                ?.takeIf { it.projectId == normalizedProjectId }
                            val reconciled = panelLayoutEngine.reconcile(
                                calculated = calculatedPanel,
                                saved = activeEditor?.draft ?: savedLayout
                            )
                            val engineering = buildProjectEngineeringSnapshot(
                                projectId = normalizedProjectId,
                                phaseMode = context.phaseMode,
                                incomer = incomer,
                                groups = groups,
                                layout = reconciled.snapshot,
                                selections = selections
                            )
                            val protectionEstimate = engineering.equipmentEstimate.protection
                            val auxiliaryEstimate = engineering.equipmentEstimate.auxiliaryLines
                                .fold(MoneyRange(0, 0, 0)) { total, line ->
                                    total + line.price
                                }
                            val catalog = apparatusCatalogProvider.get()
                            PanelVisualizationUiState.Content(
                                panel = reconciled.panel,
                                incomerAssessment = incomerAssessment,
                                protectionCostEstimate = protectionEstimate,
                                auxiliaryCostEstimate = auxiliaryEstimate,
                                totalEquipmentCost = engineering.equipmentEstimate.total,
                                equipmentEstimate = engineering.equipmentEstimate,
                                engineeringSnapshot = engineering,
                                selections = selections,
                                layoutSnapshot = reconciled.snapshot,
                                auxiliaryProducts = catalog.auxiliaryProducts
                                    .filter { it.status == ProductStatus.ACTIVE }
                                    .sortedWith(
                                        compareBy<AuxiliaryApparatusProduct> { it.function.ordinal }
                                            .thenBy { it.manufacturer }
                                            .thenBy { it.model }
                                    ),
                                apparatusCatalogVersion = catalog.catalogVersion,
                                apparatusCatalogPublishedAt = catalog.publishedAt,
                                isEditing = activeEditor != null,
                                hasUnsavedChanges = activeEditor?.let {
                                    reconciled.snapshot != it.original
                                } ?: false,
                                calculatedStructureChanged =
                                    activeEditor?.calculatedStructureChanged == true ||
                                        reconciled.calculatedStructureChanged
                            )
                        }
                    }
                        .onStart { emit(PanelVisualizationUiState.Loading) }
                        .catch { failure ->
                            emit(
                                PanelVisualizationUiState.Error(
                                    failure.message ?: "Не удалось построить компоновку щита"
                                )
                            )
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PanelVisualizationUiState.Loading
            )

    fun startEditor() {
        val content = uiState.value as? PanelVisualizationUiState.Content ?: return
        if (content.isEditing) return
        viewModelScope.launch {
            val projectId = activeProjectDataStore.activeProjectId.first().orEmpty().trim()
            if (projectId.isBlank()) return@launch
            editorSession.value = PanelEditorSession(
                projectId = projectId,
                original = content.layoutSnapshot,
                draft = content.layoutSnapshot,
                calculatedStructureChanged = content.calculatedStructureChanged
            )
        }
    }

    fun cancelEditor() {
        editorSession.value = null
        notifySuccess("Изменения компоновки отменены")
    }

    fun saveEditor() {
        val session = editorSession.value ?: return
        viewModelScope.launch {
            _selectionOperationState.value = ApparatusSelectionOperationState.Saving("panel-layout")
            runCatching {
                panelLayoutRepository.save(
                    session.projectId,
                    session.draft.copy(updatedAtEpochMs = System.currentTimeMillis())
                )
            }.onSuccess {
                editorSession.value = null
                notifySuccess("Компоновка щита сохранена")
            }.onFailure { failure ->
                notifyError(failure.message ?: "Не удалось сохранить компоновку")
            }
        }
    }

    fun moveBlock(blockId: String, direction: PanelMoveDirection) {
        mutateEditor { session, content ->
            val widths = content.panel.rails
                .flatMap { it.assemblies }
                .associate { it.layoutBlockId to it.moduleUnits }
            panelLayoutEngine.move(session.draft, blockId, direction, widths)
        }
    }

    fun moveBlockTo(blockId: String, target: PanelLayoutEngine.DropTarget) {
        mutateEditor { session, content ->
            val widths = content.panel.rails
                .flatMap { it.assemblies }
                .associate { it.layoutBlockId to it.moduleUnits }
            panelLayoutEngine.moveTo(session.draft, blockId, target, widths)
        }
    }

    fun addAuxiliaryProduct(productId: String) {
        mutateEditor { session, content ->
            val catalog = apparatusCatalogProvider.get()
            val product = catalog.auxiliaryProducts.firstOrNull { it.productId == productId }
                ?: return@mutateEditor PanelLayoutEngine.Mutation(
                    session.draft,
                    "Модель больше не найдена в каталоге",
                    false
                )
            val widths = content.panel.rails
                .flatMap { it.assemblies }
                .associate { it.layoutBlockId to it.moduleUnits }
            val number = session.draft.customModules
                .count { it.apparatus.function == product.function } + 1
            panelLayoutEngine.addCustom(
                snapshot = session.draft,
                product = product,
                customModuleId = UUID.randomUUID().toString(),
                designation = product.function.designationPrefix() + number,
                catalogVersion = catalog.catalogVersion,
                blockWidths = widths
            )
        }
    }

    fun removeCustomBlock(blockId: String) {
        mutateEditor { session, content ->
            val widths = content.panel.rails
                .flatMap { it.assemblies }
                .associate { it.layoutBlockId to it.moduleUnits }
            panelLayoutEngine.removeCustom(session.draft, blockId, widths)
        }
    }

    fun addEmptyRail() {
        mutateEditor { session, _ ->
            val next = session.draft.rails.size + 1
            if (next > ru.mugalimov.volthome.domain.model.panel.PanelEnclosureConfig.MAX_RAIL_COUNT) {
                return@mutateEditor PanelLayoutEngine.Mutation(
                    snapshot = session.draft,
                    message = "Достигнуто максимальное число DIN-реек",
                    changed = false
                )
            }
            PanelLayoutEngine.Mutation(
                snapshot = session.draft.copy(
                    enclosure = session.draft.enclosure.copy(railCount = next),
                    rails = session.draft.rails + PanelLayoutRailSnapshot(
                        id = "rail-$next",
                        itemIds = emptyList()
                    ),
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                message = "DIN-рейка добавлена",
                changed = true
            )
        }
    }

    /**
     * Меняет геометрию корпуса в текущем черновике. Возвращает true, только если новый
     * размер принят и все аппараты удалось разместить без потери.
     */
    fun resizeEnclosure(modulesPerRail: Int, railCount: Int): Boolean {
        val session = editorSession.value
        val content = uiState.value as? PanelVisualizationUiState.Content
        if (session == null || content == null) {
            notifyError("Сначала откройте редактор компоновки")
            return false
        }
        val widths = content.panel.rails
            .flatMap { it.assemblies }
            .associate { it.layoutBlockId to it.moduleUnits }
        val mutation = runCatching {
            panelLayoutEngine.resizeEnclosure(
                snapshot = session.draft,
                modulesPerRail = modulesPerRail,
                railCount = railCount,
                itemWidths = widths
            )
        }.getOrElse { failure ->
            notifyError(failure.message ?: "Не удалось изменить размер щита")
            return false
        }
        if (!mutation.changed) {
            mutation.message?.let(::notifyError)
            return false
        }
        editorSession.value = session.copy(draft = mutation.snapshot)
        notifySuccess(
            "Размер щита: ${mutation.snapshot.enclosure.railCount} × " +
                "${mutation.snapshot.enclosure.modulesPerRail} мод."
        )
        return true
    }

    fun updateCustomApparatus(
        customModuleId: String,
        userManufacturer: String?,
        userModel: String?,
        userPriceKopecks: Long?,
        connection: AuxiliaryElectricalConnection? = null
    ): Boolean {
        val session = editorSession.value
        val content = uiState.value as? PanelVisualizationUiState.Content ?: return false
        val base = session?.draft ?: content.layoutSnapshot
        val mutation = runCatching {
            panelLayoutEngine.updateCustomApparatus(
                snapshot = base,
                customModuleId = customModuleId,
                userManufacturer = userManufacturer,
                userModel = userModel,
                userPriceKopecks = userPriceKopecks,
                connection = connection
            )
        }.getOrElse {
            notifyError(it.message ?: "Не удалось сохранить данные аппарата")
            return false
        }
        if (!mutation.changed) {
            mutation.message?.let(::notifyError)
            return false
        }
        if (session != null) {
            editorSession.value = session.copy(draft = mutation.snapshot)
            notifySuccess("Модель и цена обновлены в черновике")
        } else {
            viewModelScope.launch {
                val projectId = activeProjectDataStore.activeProjectId.first().orEmpty().trim()
                runCatching { panelLayoutRepository.save(projectId, mutation.snapshot) }
                    .onSuccess { notifySuccess("Модель и цена аппарата сохранены") }
                    .onFailure {
                        notifyError(it.message ?: "Не удалось сохранить данные аппарата")
                    }
            }
        }
        return true
    }

    fun updateCustomConnection(
        customModuleId: String,
        connection: AuxiliaryElectricalConnection
    ): Boolean {
        val session = editorSession.value
        val content = uiState.value as? PanelVisualizationUiState.Content ?: return false
        val base = session?.draft ?: content.layoutSnapshot
        val mutation = runCatching {
            panelLayoutEngine.updateCustomConnection(base, customModuleId, connection)
        }.getOrElse {
            notifyError(it.message ?: "Не удалось сохранить подключение")
            return false
        }
        if (!mutation.changed) {
            mutation.message?.let(::notifyError)
            return false
        }
        if (session != null) {
            editorSession.value = session.copy(draft = mutation.snapshot)
            notifySuccess("Подключение обновлено в черновике")
        } else {
            viewModelScope.launch {
                val projectId = activeProjectDataStore.activeProjectId.first().orEmpty().trim()
                runCatching { panelLayoutRepository.save(projectId, mutation.snapshot) }
                    .onSuccess { notifySuccess("Точка подключения сохранена") }
                    .onFailure { notifyError(it.message ?: "Не удалось сохранить подключение") }
            }
        }
        return true
    }

    fun findCompatibleProducts(requiredSpec: ProtectionDeviceSpec): List<ApparatusCandidate> =
        findCompatibleApparatusUseCase(requiredSpec)

    fun selectProduct(
        slotId: String,
        requiredSpec: ProtectionDeviceSpec,
        productId: String
    ) = mutateSelection(
        slotId = slotId,
        successMessage = "Модель выбрана. Стоимость щита обновлена."
    ) { projectId ->
        val result = selectApparatusProductUseCase(
            SelectApparatusProductUseCase.Params(
                projectId = projectId,
                slotId = slotId,
                requiredSpec = requiredSpec,
                productId = productId
            )
        )
        check(result.saved) {
            when (result.failure) {
                SelectApparatusProductUseCase.SelectionFailure.PRODUCT_NOT_FOUND ->
                    "Модель больше не найдена в каталоге"
                SelectApparatusProductUseCase.SelectionFailure.INCOMPATIBLE ->
                    "Модель не соответствует рассчитанным параметрам"
                null -> "Не удалось сохранить выбранную модель"
            }
        }
    }

    fun setUserPrice(slotId: String, priceKopecks: Long?) = mutateSelection(
        slotId = slotId,
        successMessage = if (priceKopecks == null) {
            "Возвращена ориентировочная цена каталога"
        } else {
            "Ваша цена сохранена"
        }
    ) { projectId ->
        panelEquipmentRepository.setUserPrice(projectId, slotId, priceKopecks)
    }

    fun clearSelection(slotId: String) = mutateSelection(
        slotId = slotId,
        successMessage = "Модель удалена из проекта"
    ) { projectId ->
        panelEquipmentRepository.removeSelection(projectId, slotId)
    }

    fun clearSelectionMessage() {
        if (_selectionOperationState.value is ApparatusSelectionOperationState.Error ||
            _selectionOperationState.value is ApparatusSelectionOperationState.Success
        ) {
            _selectionOperationState.value = ApparatusSelectionOperationState.Idle
        }
    }

    private fun mutateEditor(
        block: (PanelEditorSession, PanelVisualizationUiState.Content) -> PanelLayoutEngine.Mutation
    ) {
        val session = editorSession.value
        val content = uiState.value as? PanelVisualizationUiState.Content
        if (session == null || content == null) {
            notifyError("Сначала откройте редактор компоновки")
            return
        }
        val mutation = runCatching { block(session, content) }.getOrElse {
            notifyError(it.message ?: "Не удалось изменить компоновку")
            return
        }
        if (mutation.changed) editorSession.value = session.copy(draft = mutation.snapshot)
        mutation.message?.let { if (mutation.changed) notifySuccess(it) else notifyError(it) }
    }

    private fun mutateSelection(
        slotId: String,
        successMessage: String,
        block: suspend (projectId: String) -> Unit
    ) {
        viewModelScope.launch {
            _selectionOperationState.value = ApparatusSelectionOperationState.Saving(slotId)
            runCatching {
                val projectId = activeProjectDataStore.activeProjectId.first().orEmpty().trim()
                check(projectId.isNotBlank()) { "Активный проект не выбран" }
                block(projectId)
            }.onSuccess {
                notifySuccess(successMessage)
            }.onFailure { failure ->
                notifyError(failure.message ?: "Не удалось сохранить изменения")
            }
        }
    }

    private fun notifySuccess(message: String) {
        _selectionOperationState.value = ApparatusSelectionOperationState.Success(message)
    }

    private fun notifyError(message: String) {
        _selectionOperationState.value = ApparatusSelectionOperationState.Error(message)
    }

    private fun AuxiliaryApparatusKind.designationPrefix(): String = when (this) {
        AuxiliaryApparatusKind.VOLTAGE_RELAY -> "KV"
        AuxiliaryApparatusKind.PHASE_CONTROL_RELAY -> "KF"
        AuxiliaryApparatusKind.CURRENT_RELAY -> "KA"
        AuxiliaryApparatusKind.MODULAR_CONTACTOR -> "KM"
        AuxiliaryApparatusKind.SURGE_PROTECTION_DEVICE -> "SPD"
    }

}

private operator fun MoneyRange.plus(other: MoneyRange): MoneyRange = MoneyRange(
    minKopecks = minKopecks + other.minKopecks,
    typicalKopecks = typicalKopecks + other.typicalKopecks,
    maxKopecks = maxKopecks + other.maxKopecks
)
