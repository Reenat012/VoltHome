package ru.mugalimov.volthome.ui.screens.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import ru.mugalimov.volthome.core.analytics.AnalyticsEvent
import ru.mugalimov.volthome.core.analytics.AnalyticsMode
import ru.mugalimov.volthome.core.analytics.AnalyticsRuntimeEntryPoint
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.core.theme.toUiPhase
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.panel.ModuleType
import ru.mugalimov.volthome.domain.model.panel.PanelAssembly
import ru.mugalimov.volthome.domain.model.panel.PanelEnclosureConfig
import ru.mugalimov.volthome.domain.model.panel.PanelGroup
import ru.mugalimov.volthome.domain.model.panel.PanelModule
import ru.mugalimov.volthome.domain.model.panel.PanelRail
import ru.mugalimov.volthome.domain.model.panel.PanelVisualization
import ru.mugalimov.volthome.domain.model.panel.PanelMoveDirection
import ru.mugalimov.volthome.domain.model.catalog.SelectedApparatusSnapshot
import ru.mugalimov.volthome.domain.model.pricing.ProtectionCostEstimate
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.incomer.IncomerAssessment
import ru.mugalimov.volthome.domain.policy.protection.RcdSelectionReason
import ru.mugalimov.volthome.ui.format.ExplicationNumberFormat
import ru.mugalimov.volthome.ui.components.IncomerAssessmentStatusCard
import ru.mugalimov.volthome.ui.format.UiTextFormat
import ru.mugalimov.volthome.ui.components.ErrorScreen
import ru.mugalimov.volthome.ui.onboarding.OnboardingRuntimeEntryPoint
import ru.mugalimov.volthome.ui.onboarding.hints.BaseHints
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor
import ru.mugalimov.volthome.ui.utilities.label
import ru.mugalimov.volthome.ui.viewmodel.PanelVisualizationUiState
import ru.mugalimov.volthome.ui.viewmodel.PanelVisualizationViewModel
import ru.mugalimov.volthome.ui.viewmodel.ApparatusSelectionOperationState
import ru.mugalimov.volthome.domain.use_case.PanelLayoutEngine
import ru.mugalimov.volthome.domain.model.project.ProjectEngineeringWarning
import android.widget.Toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Эскизная фронтальная компоновка щита.
 *
 * Экран показывает только уже рассчитанные аппараты. Это не монтажный проект
 * и не редактор электрической схемы.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelVisualizationScreen(
    fullAccess: Boolean = true,
    onUnlockClick: () -> Unit = {},
    onManualUnlockClick: () -> Unit = onUnlockClick,
    viewModel: PanelVisualizationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val onboardingCoordinator = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OnboardingRuntimeEntryPoint::class.java
        ).onboardingCoordinator()
    }
    val analytics = remember(context.applicationContext) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AnalyticsRuntimeEntryPoint::class.java
        ).analyticsTracker()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectionOperationState by viewModel.selectionOperationState.collectAsStateWithLifecycle()
    var selectedAssembly by remember { mutableStateOf<PanelAssembly?>(null) }
    var selectedModule by remember { mutableStateOf<PanelModule?>(null) }
    var selectedEditorAssembly by remember { mutableStateOf<PanelAssembly?>(null) }
    var showAuxiliaryCatalog by remember { mutableStateOf(false) }
    var showEnclosureSettings by remember { mutableStateOf(false) }
    var showCancelConfirmation by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()

    LaunchedEffect(uiState, selectedAssembly, selectedModule, selectedEditorAssembly) {
        if (
            uiState is PanelVisualizationUiState.Content &&
            selectedAssembly == null &&
            selectedModule == null &&
            selectedEditorAssembly == null &&
            !(uiState as PanelVisualizationUiState.Content).isEditing
        ) {
            val hint = BaseHints.PANEL_VISUALIZATION_OVERVIEW
            onboardingCoordinator.tryShow(
                hintId = hint.hintId,
                screen = hint.screen,
                targetTag = hint.targetTag,
                title = hint.title,
                body = hint.body
            )
        }
    }

    val contentState = uiState as? PanelVisualizationUiState.Content
    var previewTracked by remember { mutableStateOf(false) }
    var automaticOpenTracked by remember { mutableStateOf(false) }
    var previousEditing by remember { mutableStateOf(false) }

    LaunchedEffect(fullAccess, contentState != null, contentState?.isEditing) {
        val content = contentState ?: return@LaunchedEffect

        if (!fullAccess && !previewTracked) {
            val groups = content.panel.rails
                .flatMap { it.assemblies }
                .mapNotNull { it.group }
                .distinctBy { it.id }

            analytics.track(
                AnalyticsEvent.BoardPreviewShown(
                    devicesCount = groups.sumOf { it.deviceNames.size },
                    dinRailsCount = content.panel.rails.size,
                    modulesUsed = content.panel.occupiedModuleUnits,
                    modulesTotal = content.panel.totalModuleUnits
                )
            )
            previewTracked = true
        }

        if (fullAccess && !content.isEditing && !automaticOpenTracked) {
            analytics.track(
                AnalyticsEvent.BoardOpened(mode = AnalyticsMode.AUTOMATIC)
            )
            automaticOpenTracked = true
        }

        if (fullAccess && content.isEditing && !previousEditing) {
            analytics.track(
                AnalyticsEvent.BoardOpened(mode = AnalyticsMode.MANUAL)
            )
        }
        previousEditing = content.isEditing
    }

    BackHandler(enabled = contentState?.isEditing == true) {
        if (contentState?.hasUnsavedChanges == true ||
            contentState?.calculatedStructureChanged == true
        ) {
            showCancelConfirmation = true
        } else {
            viewModel.cancelEditor()
        }
    }

    LaunchedEffect(selectionOperationState) {
        when (val operation = selectionOperationState) {
            is ApparatusSelectionOperationState.Error -> {
                Toast.makeText(context, operation.message, Toast.LENGTH_LONG).show()
                viewModel.clearSelectionMessage()
            }
            is ApparatusSelectionOperationState.Success -> {
                Toast.makeText(context, operation.message, Toast.LENGTH_SHORT).show()
                viewModel.clearSelectionMessage()
            }
            ApparatusSelectionOperationState.Idle,
            is ApparatusSelectionOperationState.Saving -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PanelScreenHeader(
            fullAccess = fullAccess,
            isEditing = contentState?.isEditing == true,
            onEditClick = {
                if (fullAccess) viewModel.startEditor() else onManualUnlockClick()
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                PanelVisualizationUiState.Loading -> PanelVisualizationLoading()
                PanelVisualizationUiState.Empty -> PanelVisualizationEmpty()
                is PanelVisualizationUiState.Error -> ErrorScreen(state.message)
                is PanelVisualizationUiState.Content -> {
                    PanelVisualizationContent(
                        panel = state.panel,
                        incomerAssessment = state.incomerAssessment,
                        protectionCostEstimate = state.protectionCostEstimate,
                        auxiliaryCostEstimate = state.auxiliaryCostEstimate,
                        totalEquipmentCost = state.totalEquipmentCost,
                        engineeringWarnings = state.engineeringSnapshot.warnings,
                        selections = state.selections,
                        isEditing = state.isEditing,
                        hasCalculatedStructureChange = state.calculatedStructureChanged,
                        onAddApparatus = { showAuxiliaryCatalog = true },
                        onChangeEnclosure = { showEnclosureSettings = true },
                        onAssemblyClick = { assembly ->
                            if (!fullAccess) {
                                onUnlockClick()
                            } else if (state.isEditing || assembly.isUserAdded) {
                                selectedEditorAssembly = assembly
                            } else {
                                selectedAssembly = assembly
                            }
                        },
                        onModuleClick = { module ->
                            if (!fullAccess) {
                                onUnlockClick()
                            } else if (state.isEditing || module.isUserAdded) {
                                selectedEditorAssembly = state.panel.rails
                                    .flatMap { it.assemblies }
                                    .firstOrNull { assembly -> module in assembly.modules }
                            } else {
                                selectedModule = module
                            }
                        },
                        onMoveBlockTo = viewModel::moveBlockTo,
                        fullAccess = fullAccess,
                        onUnlockClick = onUnlockClick,
                        modifier = Modifier.padding(bottom = if (state.isEditing) 72.dp else 0.dp)
                    )

                    if (state.isEditing) {
                        PanelEditorBottomBar(
                            hasChanges = state.hasUnsavedChanges,
                            calculatedStructureChanged = state.calculatedStructureChanged,
                            onCancel = {
                                if (state.hasUnsavedChanges || state.calculatedStructureChanged) {
                                    showCancelConfirmation = true
                                } else {
                                    viewModel.cancelEditor()
                                }
                            },
                            onSave = viewModel::saveEditor,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    if (fullAccess) selectedAssembly?.let { assembly ->
        ModalBottomSheet(
            onDismissRequest = { selectedAssembly = null },
            sheetState = sheetState
        ) {
            PanelAssemblyDetails(
                assembly = assembly,
                protectionCostEstimate = (uiState as? PanelVisualizationUiState.Content)
                    ?.protectionCostEstimate,
                selections = (uiState as? PanelVisualizationUiState.Content)?.selections.orEmpty(),
                onSelectModel = { module ->
                    selectedAssembly = null
                    selectedModule = module
                },
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }


    if (fullAccess) selectedModule?.let { module ->
        val content = uiState as? PanelVisualizationUiState.Content
        val currentSelection = content?.selections?.get(module.inventorySlotId)
        val requiredSpec = module.priceSpec ?: return@let
        val candidates = remember(requiredSpec, content?.apparatusCatalogVersion) {
            viewModel.findCompatibleProducts(requiredSpec)
        }
        ModalBottomSheet(
            onDismissRequest = { selectedModule = null },
            sheetState = sheetState
        ) {
            ApparatusSelectionSheet(
                module = module,
                currentSelection = currentSelection,
                candidates = candidates,
                catalogPublishedAt = content?.apparatusCatalogPublishedAt.orEmpty(),
                isSaving = (selectionOperationState as? ApparatusSelectionOperationState.Saving)
                    ?.slotId == module.inventorySlotId,
                onSelectProduct = { productId ->
                    viewModel.selectProduct(
                        slotId = module.inventorySlotId,
                        requiredSpec = requiredSpec,
                        productId = productId
                    )
                    sheetScope.launch {
                        sheetState.hide()
                        selectedModule = null
                    }
                },
                onSaveUserPrice = { priceKopecks ->
                    viewModel.setUserPrice(module.inventorySlotId, priceKopecks)
                },
                onClearSelection = {
                    viewModel.clearSelection(module.inventorySlotId)
                },
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }

    if (fullAccess) selectedEditorAssembly?.let { assembly ->
        ModalBottomSheet(
            onDismissRequest = { selectedEditorAssembly = null },
            sheetState = sheetState
        ) {
            PanelBlockEditorSheet(
                assembly = assembly,
                isEditing = contentState?.isEditing == true,
                groups = contentState?.engineeringSnapshot?.groups.orEmpty(),
                onMove = { direction ->
                    viewModel.moveBlock(assembly.layoutBlockId, direction)
                },
                onDelete = {
                    viewModel.removeCustomBlock(assembly.layoutBlockId)
                    selectedEditorAssembly = null
                },
                onSaveCustomDetails = { customModuleId, manufacturer, model, price, connection ->
                    viewModel.updateCustomApparatus(
                        customModuleId = customModuleId,
                        userManufacturer = manufacturer,
                        userModel = model,
                        userPriceKopecks = price,
                        connection = connection
                    ).also { saved ->
                        if (saved) selectedEditorAssembly = null
                    }
                },
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }

    if (fullAccess && showAuxiliaryCatalog) {
        ModalBottomSheet(
            onDismissRequest = { showAuxiliaryCatalog = false },
            sheetState = sheetState
        ) {
            AuxiliaryCatalogSheet(
                products = contentState?.auxiliaryProducts.orEmpty(),
                onAdd = { productId ->
                    viewModel.addAuxiliaryProduct(productId)
                    sheetScope.launch {
                        sheetState.hide()
                        showAuxiliaryCatalog = false
                    }
                },
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }

    if (fullAccess && showEnclosureSettings) {
        val content = contentState
        if (content != null) {
            ModalBottomSheet(
                onDismissRequest = { showEnclosureSettings = false },
                sheetState = sheetState
            ) {
                PanelEnclosureSheet(
                    current = content.layoutSnapshot.enclosure,
                    occupiedModuleUnits = content.panel.occupiedModuleUnits,
                    onApply = { modulesPerRail, railCount ->
                        val accepted = viewModel.resizeEnclosure(modulesPerRail, railCount)
                        if (accepted) {
                            sheetScope.launch {
                                sheetState.hide()
                                showEnclosureSettings = false
                            }
                        }
                        accepted
                    },
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }

    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Отменить изменения?") },
            text = { Text("Несохранённая компоновка будет потеряна.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirmation = false
                    selectedEditorAssembly = null
                    viewModel.cancelEditor()
                }) { Text("Отменить изменения") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmation = false }) {
                    Text("Продолжить редактирование")
                }
            }
        )
    }
}

@Composable
private fun PanelScreenHeader(
    fullAccess: Boolean,
    isEditing: Boolean,
    onEditClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Щит",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Компоновка и стоимость аппаратов",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!fullAccess) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "PRO",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else if (isEditing) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "РЕДАКТОР",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else {
            TextButton(onClick = onEditClick) {
                Text("Редактировать")
            }
        }
    }
}

@Composable
fun PanelVisualizationContent(
    panel: PanelVisualization,
    protectionCostEstimate: ProtectionCostEstimate,
    incomerAssessment: IncomerAssessment? = null,
    modifier: Modifier = Modifier,
    auxiliaryCostEstimate: MoneyRange = MoneyRange(0, 0, 0),
    totalEquipmentCost: MoneyRange = protectionCostEstimate.total,
    engineeringWarnings: List<ProjectEngineeringWarning> = emptyList(),
    selections: Map<String, SelectedApparatusSnapshot>,
    onAssemblyClick: (PanelAssembly) -> Unit,
    onModuleClick: (PanelModule) -> Unit,
    onMoveBlockTo: (String, PanelLayoutEngine.DropTarget) -> Unit = { _, _ -> },
    fullAccess: Boolean = true,
    onUnlockClick: () -> Unit = {},
    isEditing: Boolean = false,
    hasCalculatedStructureChange: Boolean = false,
    onAddApparatus: () -> Unit = {},
    onChangeEnclosure: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    var previousIsEditing by remember { mutableStateOf(isEditing) }

    LaunchedEffect(isEditing) {
        val editorWasClosed = previousIsEditing && !isEditing
        previousIsEditing = isEditing
        if (editorWasClosed) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isEditing) {
            item(key = "panel_editor_banner") {
                PanelEditorBanner(
                    calculatedStructureChanged = hasCalculatedStructureChange,
                    enclosure = panel.enclosureConfig(),
                    onAddApparatus = onAddApparatus,
                    onChangeEnclosure = onChangeEnclosure
                )
            }
        }

        item(key = "panel_summary") {
            PanelSummary(
                panel = panel,
                protectionCostEstimate = protectionCostEstimate,
                auxiliaryCostEstimate = auxiliaryCostEstimate,
                totalEquipmentCost = totalEquipmentCost,
                modifier = Modifier.onboardingAnchor(
                    targetTag = OnboardingTargetTag.PANEL_SUMMARY,
                    screenId = OnboardingScreen.PANEL_VISUALIZATION
                )
            )
        }

        incomerAssessment?.let { assessment ->
            item(key = "panel_incomer_assessment") {
                IncomerAssessmentStatusCard(assessment = assessment)
            }
        }

        if (engineeringWarnings.isNotEmpty()) {
            item(key = "panel_engineering_warnings") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.68f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Нужно уточнить подключение",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        engineeringWarnings.forEach { warning ->
                            Text(
                                text = "• ${warning.message}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "Аппараты не удалены: они остаются в компоновке и смете, " +
                                "но схема пометит их как неподключённые.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        }

        item(key = "panel_cabinet") {
            PanelCabinet(
                panel = panel,
                selections = selections,
                onAssemblyClick = onAssemblyClick,
                onModuleClick = onModuleClick,
                isEditing = isEditing,
                onMoveBlockTo = onMoveBlockTo,
                fullAccess = fullAccess,
                onUnlockClick = onUnlockClick
            )
        }

        if (!fullAccess) {
            item(key = "panel_pro_unlock") {
                PanelProUnlockCard(
                    panel = panel,
                    estimate = protectionCostEstimate,
                    onUnlockClick = onUnlockClick
                )
            }
        }

        item(key = "panel_disclaimer") {
            Text(
                text = if (fullAccess) {
                    "Эскиз показывает компоновку рассчитанных аппаратов и не заменяет монтажный проект."
                } else {
                    "Показан фрагмент рассчитанной компоновки. Полный щит и выбор аппаратов доступны в PRO."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PanelProUnlockCard(
    panel: PanelVisualization,
    estimate: ProtectionCostEstimate,
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Полная компоновка готова",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "${panel.rails.size} DIN-реек · ${estimate.totalDeviceCount} аппаратов · " +
                    "≈ ${formatEstimateRubles(estimate.total.typicalKopecks)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "В PRO откроются все рейки, выбор конкретных моделей и собственные закупочные цены.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Button(
                onClick = onUnlockClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть полный щит в PRO")
            }
        }
    }
}

@Composable
private fun PanelSummary(
    panel: PanelVisualization,
    protectionCostEstimate: ProtectionCostEstimate,
    auxiliaryCostEstimate: MoneyRange,
    totalEquipmentCost: MoneyRange,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Эскиз компоновки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "${panel.occupiedModuleUnits}/${panel.totalModuleUnits} мод.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Реек: ${panel.rails.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Групп: ${panel.groupsCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Резерв: ${panel.freeModuleUnits}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Корпус: ${panel.rails.size} × " +
                    "${panel.rails.firstOrNull()?.capacityModuleUnits ?: 0} мод. · " +
                    "всего ${panel.totalModuleUnits}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            PhaseLegend()

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Аппараты защиты",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "≈ ${formatEstimateRubles(protectionCostEstimate.total.typicalKopecks)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (auxiliaryCostEstimate.typicalKopecks > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Добавлено вручную",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "≈ ${formatEstimateRubles(auxiliaryCostEstimate.typicalKopecks)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Итого оборудование",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "≈ ${formatEstimateRubles(totalEquipmentCost.typicalKopecks)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = if (protectionCostEstimate.selectedProductCount == 0) {
                    "Смета пока рассчитана по средним ценам"
                } else {
                    "Выбрано моделей: ${protectionCostEstimate.selectedProductCount}/" +
                        protectionCostEstimate.totalDeviceCount
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatEstimateRubles(kopecks: Long): String =
    UiTextFormat.rubles(kopecks)

private fun PanelVisualization.enclosureConfig(): PanelEnclosureConfig = PanelEnclosureConfig(
    modulesPerRail = rails.firstOrNull()?.capacityModuleUnits
        ?: ru.mugalimov.volthome.domain.model.panel.PanelLayoutSnapshot.DEFAULT_RAIL_CAPACITY,
    railCount = rails.size.coerceAtLeast(1)
)

@Composable
private fun PhaseLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Phase.entries.forEach { phase ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(phase.color())
                )
                Text(
                    text = "Фаза ${phase.displayLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class PanelDragState(
    val itemId: String,
    val assembly: PanelAssembly,
    val pointerRoot: Offset,
    val pointerDelta: Offset,
    val previewTarget: PanelLayoutEngine.DropTarget?,
    val previewOrder: List<String>
)

private class PanelPlacementMotion {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    var targetPosition: Offset? = null
    var animationJob: Job? = null
}

internal data class PanelRailDragGeometry(
    val viewportBounds: Rect,
    val contentBounds: Rect
)

internal fun panelPlacementDelta(previous: Offset?, current: Offset): Offset? {
    if (previous == null ||
        !previous.isFinitePanelPosition() ||
        !current.isFinitePanelPosition() ||
        previous == current
    ) return null
    return previous - current
}

internal fun panelInterruptedPlacementDelta(
    previousTarget: Offset?,
    currentOffset: Offset,
    newTarget: Offset
): Offset? = panelPlacementDelta(
    previous = previousTarget?.plus(currentOffset),
    current = newTarget
)

private fun Offset.isFinitePanelPosition(): Boolean = x.isFinite() && y.isFinite()

private fun Rect.isFinitePanelBounds(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()

internal fun resolvePanelDropTarget(
    pointerRoot: Offset,
    draggedId: String,
    panel: PanelVisualization,
    previewRails: List<PanelRail>,
    railGeometry: Map<Int, PanelRailDragGeometry>
): PanelLayoutEngine.DropTarget? {
    if (!pointerRoot.isFinitePanelPosition() || railGeometry.isEmpty()) return null
    val railIndex = railGeometry.entries
        .filter { (_, geometry) ->
            val bounds = geometry.viewportBounds
            pointerRoot.y in (bounds.top - 32f)..(bounds.bottom + 32f)
        }
        .minByOrNull { (_, geometry) ->
            abs(pointerRoot.y - geometry.viewportBounds.center.y)
        }
        ?.key
        ?: return null
    val geometry = railGeometry[railIndex] ?: return null
    if (!geometry.contentBounds.isFinitePanelBounds() || geometry.contentBounds.width <= 0f) {
        return null
    }
    val rail = previewRails.getOrNull(railIndex) ?: return null
    val assemblies = rail.assemblies.filterNot { it.layoutBlockId == draggedId }
    val unitWidth = geometry.contentBounds.width / rail.capacityModuleUnits.coerceAtLeast(1)
    if (!unitWidth.isFinite() || unitWidth <= 0f) return null
    val pointerModule = ((pointerRoot.x - geometry.contentBounds.left) / unitWidth)
        .coerceIn(0f, rail.capacityModuleUnits.toFloat())
    var occupiedModules = 0f
    val itemIndex = assemblies.indexOfFirst { assembly ->
        val centerModule = occupiedModules + assembly.moduleUnits / 2f
        val beforeCenter = pointerModule < centerModule
        occupiedModules += assembly.moduleUnits
        beforeCenter
    }.let { if (it < 0) assemblies.size else it }
    val requestedGlobalIndex = previewRails.take(railIndex)
        .sumOf { rail ->
            rail.assemblies.count { assembly -> assembly.layoutBlockId != draggedId }
        } + itemIndex
    return dropTargetForGlobalIndex(panel, draggedId, requestedGlobalIndex)
}

internal fun dropTargetForGlobalIndex(
    panel: PanelVisualization,
    draggedId: String,
    requestedIndex: Int
): PanelLayoutEngine.DropTarget {
    val rails = panel.rails.map { rail ->
        rail.assemblies.map(PanelAssembly::layoutBlockId).filterNot { it == draggedId }
    }
    var remaining = requestedIndex.coerceIn(0, rails.sumOf(List<String>::size))
    rails.forEachIndexed { railIndex, ids ->
        if (remaining <= ids.size || railIndex == rails.lastIndex) {
            return PanelLayoutEngine.DropTarget(railIndex, remaining.coerceAtMost(ids.size))
        }
        remaining -= ids.size
    }
    return PanelLayoutEngine.DropTarget(0, 0)
}

internal fun previewPanelOrder(
    panel: PanelVisualization,
    draggedId: String,
    target: PanelLayoutEngine.DropTarget
): List<String> {
    val railsWithoutDragged = panel.rails.map { rail ->
        rail.assemblies.map(PanelAssembly::layoutBlockId).filterNot { it == draggedId }
    }
    val order = railsWithoutDragged.flatten().toMutableList()
    val targetIndex = (
        railsWithoutDragged.take(target.railIndex).sumOf(List<String>::size) +
            target.itemIndex.coerceIn(0, railsWithoutDragged[target.railIndex].size)
        ).coerceIn(0, order.size)
    order.add(targetIndex, draggedId)
    return order
}

private fun repackPanelForPreview(
    panel: PanelVisualization,
    orderedIds: List<String>,
    assembliesById: Map<String, PanelAssembly>
): List<PanelRail> {
    val capacity = panel.rails.firstOrNull()?.capacityModuleUnits ?: 12
    val packed = MutableList(panel.rails.size.coerceAtLeast(1)) { mutableListOf<PanelAssembly>() }
    var railIndex = 0
    var occupied = 0
    orderedIds.distinct().forEach { id ->
        val assembly = assembliesById[id] ?: return@forEach
        if (occupied + assembly.moduleUnits > capacity) {
            railIndex++
            occupied = 0
        }
        if (railIndex >= packed.size) packed += mutableListOf<PanelAssembly>()
        packed[railIndex] += assembly
        occupied += assembly.moduleUnits
    }
    return packed.mapIndexed { index, assemblies ->
        PanelRail(
            number = index + 1,
            capacityModuleUnits = capacity,
            assemblies = assemblies
        )
    }
}

@Composable
private fun PanelCabinet(
    panel: PanelVisualization,
    selections: Map<String, SelectedApparatusSnapshot>,
    onAssemblyClick: (PanelAssembly) -> Unit,
    onModuleClick: (PanelModule) -> Unit,
    isEditing: Boolean,
    onMoveBlockTo: (String, PanelLayoutEngine.DropTarget) -> Unit,
    fullAccess: Boolean,
    onUnlockClick: () -> Unit
) {
    var cabinetRoot by remember { mutableStateOf(Offset.Zero) }
    var cabinetCoordinates by remember {
        mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null)
    }
    var dragState by remember { mutableStateOf<PanelDragState?>(null) }
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    val railGeometry = remember { mutableStateMapOf<Int, PanelRailDragGeometry>() }
    val placementMotions = remember { mutableMapOf<String, PanelPlacementMotion>() }
    val placementScope = rememberCoroutineScope()
    val flatOrder = panel.rails.flatMap { rail -> rail.assemblies.map { it.layoutBlockId } }
    val assembliesById = panel.rails
        .flatMap(PanelRail::assemblies)
        .associateBy(PanelAssembly::layoutBlockId)

    fun placementMotionFor(itemId: String): PanelPlacementMotion =
        placementMotions.getOrPut(itemId, ::PanelPlacementMotion)

    fun updatePlacementTarget(itemId: String, newTarget: Offset) {
        if (!newTarget.isFinitePanelPosition()) return
        val motion = placementMotionFor(itemId)
        val previousTarget = motion.targetPosition
        if (previousTarget != null && (previousTarget - newTarget).getDistance() < 0.5f) {
            return
        }

        val startOffset = panelInterruptedPlacementDelta(
            previousTarget = previousTarget,
            currentOffset = motion.offset.value,
            newTarget = newTarget
        )
        motion.targetPosition = newTarget
        motion.animationJob?.cancel()

        val shouldAnimate = dragState != null && dragState?.itemId != itemId
        motion.animationJob = placementScope.launch {
            if (!shouldAnimate || startOffset == null) {
                motion.offset.snapTo(Offset.Zero)
                return@launch
            }
            motion.offset.snapTo(startOffset)
            motion.offset.animateTo(
                targetValue = Offset.Zero,
                animationSpec = tween(durationMillis = 190)
            )
        }
    }

    LaunchedEffect(isEditing, dragState?.itemId) {
        if (!isEditing || dragState == null) {
            placementMotions.values.forEach { motion ->
                motion.animationJob?.cancel()
                motion.animationJob = null
                motion.offset.snapTo(Offset.Zero)
                motion.targetPosition = null
            }
        }
    }

    LaunchedEffect(flatOrder) {
        val activeIds = flatOrder.toSet()
        placementMotions.keys
            .filterNot(activeIds::contains)
            .forEach { removedId ->
                placementMotions.remove(removedId)?.animationJob?.cancel()
            }
    }

    fun updatePreview(pointerRoot: Offset) {
        if (!pointerRoot.isFinitePanelPosition()) return
        val active = dragState ?: return
        val previewRails = repackPanelForPreview(
            panel = panel,
            orderedIds = active.previewOrder,
            assembliesById = assembliesById
        )
        val target = resolvePanelDropTarget(
            pointerRoot = pointerRoot,
            draggedId = active.itemId,
            panel = panel,
            previewRails = previewRails,
            railGeometry = railGeometry
        ) ?: return
        if (target == active.previewTarget) return
        val previewOrder = previewPanelOrder(panel, active.itemId, target)
        dragState = active.copy(
            pointerRoot = pointerRoot,
            previewTarget = target,
            previewOrder = previewOrder
        )
    }

    fun finishDrag() {
        val finished = dragState
        dragState = null
        finished?.previewTarget?.let { target ->
            onMoveBlockTo(finished.itemId, target)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                val position = it.positionInRoot()
                if (position.isFinitePanelPosition()) {
                    cabinetRoot = position
                    cabinetCoordinates = it
                } else {
                    cabinetCoordinates = null
                }
            }
            .pointerInput(isEditing, panel) {
                if (isEditing) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pointerLocal ->
                            val coordinates = cabinetCoordinates
                                ?.takeIf { it.isAttached }
                                ?: return@detectDragGesturesAfterLongPress
                            val pointerRoot = coordinates.localToRoot(pointerLocal)
                            if (!pointerRoot.isFinitePanelPosition()) {
                                return@detectDragGesturesAfterLongPress
                            }
                            val selectedEntry = itemBounds.entries.firstOrNull { (_, bounds) ->
                                bounds.isFinitePanelBounds() && bounds.contains(pointerRoot)
                            } ?: return@detectDragGesturesAfterLongPress
                            val assembly = assembliesById[selectedEntry.key]
                                ?: return@detectDragGesturesAfterLongPress
                            dragState = PanelDragState(
                                itemId = assembly.layoutBlockId,
                                assembly = assembly,
                                pointerRoot = pointerRoot,
                                pointerDelta = pointerRoot - selectedEntry.value.topLeft,
                                previewTarget = null,
                                previewOrder = flatOrder
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val coordinates = cabinetCoordinates
                                ?.takeIf { it.isAttached }
                                ?: return@detectDragGesturesAfterLongPress
                            val pointerRoot = coordinates.localToRoot(change.position)
                            if (pointerRoot.isFinitePanelPosition()) {
                                dragState = dragState?.copy(pointerRoot = pointerRoot)
                                updatePreview(pointerRoot)
                            }
                        },
                        onDragEnd = ::finishDrag,
                        onDragCancel = { dragState = null }
                    )
                }
            },
        shape = RoundedCornerShape(24.dp),
        color = VhColors.tokens.surfaceAlt,
        border = BorderStroke(
            if (isEditing) 2.dp else 1.dp,
            if (isEditing) MaterialTheme.colorScheme.primary else VhColors.tokens.borderStrong
        ),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CabinetFasteners()

            val visibleRails = if (fullAccess) panel.rails else panel.rails.take(1)
            val displayOrder = dragState?.previewOrder ?: flatOrder
            val displayRails = repackPanelForPreview(
                panel = panel,
                orderedIds = displayOrder,
                assembliesById = assembliesById
            )
            displayRails.take(visibleRails.size).forEach { rail ->
                DinRailView(
                    rail = rail,
                    selections = selections,
                    onAssemblyClick = onAssemblyClick,
                    onModuleClick = onModuleClick,
                    isEditing = isEditing,
                    draggedItemId = dragState?.itemId,
                    placementMotionFor = ::placementMotionFor,
                    onPlacementTargetChanged = ::updatePlacementTarget,
                    onItemBounds = { id, bounds ->
                        if (bounds.isFinitePanelBounds()) itemBounds[id] = bounds
                    },
                    onRailGeometry = { index, geometry ->
                        if (geometry.viewportBounds.isFinitePanelBounds() &&
                            geometry.contentBounds.isFinitePanelBounds()
                        ) {
                            railGeometry[index] = geometry
                        }
                    }
                )
            }

            if (!fullAccess && panel.rails.size > visibleRails.size) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onUnlockClick),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                ) {
                    Text(
                        text = "Ещё ${panel.rails.size - visibleRails.size} DIN-реек доступны в PRO",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            CabinetFasteners()
        }

        dragState?.let { active ->
            Box(modifier = Modifier.fillMaxSize()) {
                PanelDragGhost(
                    assembly = active.assembly,
                    selections = selections,
                    position = active.pointerRoot - active.pointerDelta - cabinetRoot,
                    widthPx = itemBounds[active.itemId]?.width,
                    modifier = Modifier.zIndex(10f)
                )
            }
        }
    }
}

@Composable
private fun CabinetFasteners() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CabinetScrew()
        CabinetScrew()
    }
}

@Composable
private fun CabinetScrew() {
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(VhColors.tokens.border),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(1.dp)
                .background(VhColors.tokens.textMuted)
        )
    }
}

@Composable
private fun DinRailView(
    rail: PanelRail,
    selections: Map<String, SelectedApparatusSnapshot>,
    onAssemblyClick: (PanelAssembly) -> Unit,
    onModuleClick: (PanelModule) -> Unit,
    isEditing: Boolean,
    draggedItemId: String?,
    placementMotionFor: (String) -> PanelPlacementMotion,
    onPlacementTargetChanged: (String, Offset) -> Unit,
    onItemBounds: (String, Rect) -> Unit,
    onRailGeometry: (Int, PanelRailDragGeometry) -> Unit
) {
    var viewportBounds by remember(rail.number) { mutableStateOf<Rect?>(null) }
    var contentBounds by remember(rail.number) { mutableStateOf<Rect?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DIN-рейка ${rail.number}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = VhColors.tokens.textSecondary
            )
            Text(
                text = "${rail.occupiedModuleUnits}/${rail.capacityModuleUnits} мод.",
                style = MaterialTheme.typography.labelMedium,
                color = VhColors.tokens.textMuted
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val viewport = coordinates.boundsInRoot()
                    viewportBounds = viewport
                    contentBounds?.let { content ->
                        onRailGeometry(
                            rail.number - 1,
                            PanelRailDragGeometry(viewport, content)
                        )
                    }
                }
                .border(
                    width = 1.dp,
                    color = VhColors.tokens.border,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(4.dp)
        ) {
            val unitWidth = maxOf(
                maxWidth / rail.capacityModuleUnits.toFloat(),
                MIN_VISIBLE_MODULE_WIDTH
            )
            val contentWidth = unitWidth * rail.capacityModuleUnits.toFloat()
            val horizontalScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(contentWidth)
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            val content = Rect(
                                offset = position,
                                size = Size(
                                    coordinates.size.width.toFloat(),
                                    coordinates.size.height.toFloat()
                                )
                            )
                            contentBounds = content
                            viewportBounds?.let { viewport ->
                                onRailGeometry(
                                    rail.number - 1,
                                    PanelRailDragGeometry(viewport, content)
                                )
                            }
                        }
                ) {
                    DinMetalRail(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        rail.assemblies.forEach { assembly ->
                            key(assembly.layoutBlockId) {
                                PanelAssemblyView(
                                    assembly = assembly,
                                    unitWidth = unitWidth,
                                    selections = selections,
                                    onAssemblyClick = { onAssemblyClick(assembly) },
                                    onModuleClick = onModuleClick,
                                    isEditing = isEditing,
                                    isDragged = assembly.layoutBlockId == draggedItemId,
                                    placementMotion = placementMotionFor(assembly.layoutBlockId),
                                    onPlacementTargetChanged = onPlacementTargetChanged,
                                    onBoundsChanged = onItemBounds
                                )
                            }
                        }

                        if (rail.freeModuleUnits > 0) {
                            EmptyModuleSlots(
                                count = rail.freeModuleUnits,
                                unitWidth = unitWidth
                            )
                        }
                    }
                }
            }
        }
    }
}

private val MIN_VISIBLE_MODULE_WIDTH = 26.dp

@Composable
private fun DinMetalRail(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(18.dp)
            .background(
                color = VhColors.tokens.textDisabled,
                shape = RoundedCornerShape(2.dp)
            )
            .border(
                width = 1.dp,
                color = VhColors.tokens.textMuted,
                shape = RoundedCornerShape(2.dp)
            )
    )
}

@Composable
private fun PanelAssemblyView(
    assembly: PanelAssembly,
    unitWidth: Dp,
    selections: Map<String, SelectedApparatusSnapshot>,
    onAssemblyClick: () -> Unit,
    onModuleClick: (PanelModule) -> Unit,
    isEditing: Boolean,
    isDragged: Boolean,
    placementMotion: PanelPlacementMotion,
    onPlacementTargetChanged: (String, Offset) -> Unit,
    onBoundsChanged: (String, Rect) -> Unit
) {
    val width = unitWidth * assembly.moduleUnits.toFloat()
    if (isDragged) {
        PanelAssemblyDropPlaceholder(
            width = width
        )
        return
    }
    val description = buildString {
        append(assembly.title)
        assembly.group?.let { append(", фаза ${it.phase.displayLabel()}") }
        append(if (isEditing) ". Нажмите для перемещения" else ". Нажмите, чтобы открыть параметры")
    }

    Column(
        modifier = Modifier
            .width(width)
            .onGloballyPositioned { layoutCoordinates ->
                val targetPosition = layoutCoordinates.positionInRoot()
                if (!targetPosition.isFinitePanelPosition()) {
                    placementMotion.targetPosition = null
                    return@onGloballyPositioned
                }
                onPlacementTargetChanged(assembly.layoutBlockId, targetPosition)
                onBoundsChanged(
                    assembly.layoutBlockId,
                    Rect(
                        targetPosition,
                        Size(
                            layoutCoordinates.size.width.toFloat(),
                            layoutCoordinates.size.height.toFloat()
                        )
                    )
                )
            }
            .graphicsLayer {
                translationX = placementMotion.offset.value.x
                translationY = placementMotion.offset.value.y
            }
            .then(
                if (isEditing) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                        RoundedCornerShape(5.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onAssemblyClick)
            .semantics { contentDescription = description }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            assembly.modules.forEach { module ->
                PanelModuleView(
                    module = module,
                    width = unitWidth * module.moduleUnits.toFloat(),
                    selection = selections[module.inventorySlotId],
                    onClick = { onModuleClick(module) },
                    enabled = !isEditing
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clickable(onClick = onAssemblyClick),
            color = if (assembly.group == null) {
                VhColors.tokens.primarySurface
            } else {
                VhColors.tokens.surface
            },
            border = BorderStroke(0.5.dp, VhColors.tokens.border)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        assembly.group != null -> assembly.group.shortLabel
                        assembly.isUserAdded -> assembly.title
                        else -> "Ввод"
                    },
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VhColors.tokens.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PanelAssemblyDropPlaceholder(
    width: Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(142.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = 1.dp)
                .width(2.dp)
                .height(116.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                    shape = RoundedCornerShape(1.dp)
                )
        )
    }
}

@Composable
private fun PanelDragGhost(
    assembly: PanelAssembly,
    selections: Map<String, SelectedApparatusSnapshot>,
    position: Offset,
    widthPx: Float?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val width = with(density) { (widthPx ?: 64f).toDp() }
    Surface(
        modifier = modifier
            .graphicsLayer {
                translationX = position.x
                translationY = position.y
                scaleX = 1.02f
                scaleY = 1.02f
            }
            .width(width),
        color = VhColors.tokens.surfaceAlt,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 0.dp
    ) {
        Column {
            val unitWidth = width / assembly.moduleUnits.coerceAtLeast(1).toFloat()
            Row {
                assembly.modules.forEach { module ->
                    PanelModuleView(
                        module = module,
                        width = unitWidth * module.moduleUnits.toFloat(),
                        selection = selections[module.inventorySlotId],
                        onClick = {},
                        enabled = false
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = assembly.group?.shortLabel ?: assembly.title,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PanelModuleView(
    module: PanelModule,
    width: Dp,
    selection: SelectedApparatusSnapshot?,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .width(width)
            .height(112.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color = VhColors.tokens.surface,
        border = BorderStroke(0.5.dp, VhColors.tokens.borderStrong),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(module.phase?.color() ?: VhColors.tokens.primary)
            )

            Text(
                text = module.designation,
                fontSize = 7.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.Bold,
                color = VhColors.tokens.textSecondary,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = module.type.shortTitle(),
                fontSize = 7.sp,
                lineHeight = 8.sp,
                color = VhColors.tokens.textMuted,
                maxLines = 1
            )

            Spacer(Modifier.height(4.dp))

            ModuleLever(module = module)

            Spacer(Modifier.height(4.dp))

            Text(
                text = module.mainParam(),
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VhColors.tokens.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Text(
                text = module.secondaryParam(),
                fontSize = 7.sp,
                lineHeight = 8.sp,
                color = VhColors.tokens.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val displayedManufacturer = selection?.manufacturer
                ?: module.auxiliarySnapshot?.displayManufacturer
            val displayedModel = selection?.model
                ?: module.auxiliarySnapshot?.displayModel
            displayedManufacturer?.let { manufacturer ->
                Text(
                    text = manufacturer.panelBrandShort(),
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VhColors.tokens.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                )
            }
            displayedModel?.takeIf(String::isNotBlank)?.let { model ->
                Text(
                    text = model,
                    fontSize = 6.sp,
                    lineHeight = 7.sp,
                    color = VhColors.tokens.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ModuleLever(module: PanelModule) {
    val leverColor = when (module.type) {
        ModuleType.BREAKER -> VhColors.tokens.textSecondary
        ModuleType.RCD -> VhColors.tokens.info
        ModuleType.RCBO -> VhColors.tokens.warning
        ModuleType.VOLTAGE_RELAY,
        ModuleType.PHASE_CONTROL_RELAY,
        ModuleType.CURRENT_RELAY -> VhColors.tokens.primary
        ModuleType.MODULAR_CONTACTOR -> VhColors.tokens.success
        ModuleType.SURGE_PROTECTION_DEVICE -> VhColors.tokens.warning
    }

    Surface(
        modifier = Modifier
            .widthIn(min = 10.dp, max = 24.dp)
            .fillMaxWidth(0.42f)
            .height(25.dp),
        shape = RoundedCornerShape(2.dp),
        color = VhColors.tokens.bg,
        border = BorderStroke(1.dp, VhColors.tokens.border)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(leverColor, RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun EmptyModuleSlots(
    count: Int,
    unitWidth: Dp
) {
    Column(modifier = Modifier.width(unitWidth * count.toFloat())) {
        Row(modifier = Modifier.height(112.dp)) {
            repeat(count) {
                Box(
                    modifier = Modifier
                        .width(unitWidth)
                        .height(112.dp)
                        .background(VhColors.tokens.bg.copy(alpha = 0.36f))
                        .border(0.5.dp, VhColors.tokens.borderDisabled),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(VhColors.tokens.border)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .border(0.5.dp, VhColors.tokens.borderDisabled),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Резерв: $count мод.",
                fontSize = 7.sp,
                lineHeight = 8.sp,
                color = VhColors.tokens.textDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PanelAssemblyDetails(
    assembly: PanelAssembly,
    protectionCostEstimate: ProtectionCostEstimate?,
    selections: Map<String, SelectedApparatusSnapshot>,
    onSelectModel: (PanelModule) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val group = assembly.group
    var showAllDevices by remember(assembly.id) { mutableStateOf(false) }
    val shownDeviceNames = group?.deviceNames.orEmpty().let { names ->
        if (showAllDevices) names else names.take(4)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = if (group == null) "Вводная защита" else "Группа ${group.number}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (group != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${group.roomName} · ${group.type.label(context)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DetailRow(
                        label = "Фаза",
                        value = group.phase.displayLabel(),
                        valueColor = group.phase.color()
                    )
                    DetailRow(label = "Кабель", value = group.cableLabel)
                    DetailRow(
                        label = "Установленная мощность",
                        value = "${ExplicationNumberFormat.kwFromW(group.installedPowerW, decimals = 1)}${UiTextFormat.NBSP}кВт"
                    )
                }
            }
        }

        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        item {
            Text(
                text = "Аппараты",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(assembly.modules, key = { it.id }) { module ->
            ModuleDetailsCard(
                module = module,
                selection = selections[module.inventorySlotId],
                unitPrice = protectionCostEstimate?.lines
                    ?.firstOrNull { module.inventorySlotId in it.slotIds }
                    ?.unitPrice,
                catalogUpdatedAt = protectionCostEstimate?.catalogUpdatedAt,
                onSelectModel = { onSelectModel(module) }
            )
        }

        if (group != null) {
            item { CalculationExplanationCard(group) }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item {
                Text(
                    text = "Подключённые устройства",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (group.deviceNames.isEmpty()) {
                item {
                    Text(
                        text = "Устройства не указаны",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(shownDeviceNames) { deviceName ->
                    Text(
                        text = "• $deviceName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (group.deviceNames.size > 4) {
                    item {
                        TextButton(onClick = { showAllDevices = !showAllDevices }) {
                            Text(
                                if (showAllDevices) "Свернуть список"
                                else "Показать ещё ${group.deviceNames.size - 4}"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculationExplanationCard(group: PanelGroup) {
    val rcdReason = when {
        RcdSelectionReason.SPECIAL_ROOM.name in group.rcdReasonCodes ->
            "УЗО ${group.rcdCurrent}${UiTextFormat.NBSP}мА назначено из-за типа помещения."
        RcdSelectionReason.GENERAL_PURPOSE_SOCKET.name in group.rcdReasonCodes ->
            "УЗО ${group.rcdCurrent}${UiTextFormat.NBSP}мА назначено из-за элемента «Розетка бытовая»."
        RcdSelectionReason.SOCKET_CONNECTED_LOAD.name in group.rcdReasonCodes ->
            "УЗО ${group.rcdCurrent}${UiTextFormat.NBSP}мА назначено, потому что устройства этой группы подключаются через розетку."
        group.rcdRequired ->
            "УЗО сохранено старой версией или изменено вручную; точная причина отсутствует."
        else ->
            "Отдельное УЗО не назначено: нет особого помещения, бытовой розетки или нагрузки с розеточным подключением."
    }
    val source = when (group.calculationSource) {
        CalculationSource.AUTO -> "Автоматический расчёт"
        CalculationSource.MANUAL -> "Изменено вручную"
        CalculationSource.LEGACY -> "Результат предыдущей версии"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "Почему выбрана эта связка",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Автомат ${group.breakerType}${group.circuitBreaker} выбран по установленному току " +
                        "${group.installedCurrentA.formatCurrent()}${UiTextFormat.NBSP}А и минимальному номиналу для типа линии.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = group.voltageDropPercent?.let { voltageDrop ->
                    "Кабель ${group.cableLabel}: допустимый ток после поправок " +
                        "${group.correctedAmpacityA?.formatCurrent() ?: "—"}${UiTextFormat.NBSP}А, " +
                        "падение напряжения ${voltageDrop.formatCurrent()}%."
                } ?: "Кабель ${group.cableLabel} выбран предварительно по автомату. " +
                    "Укажите длину и условия линии на экране «Линии», чтобы выполнить полную проверку.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = rcdReason,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$source · алгоритм ${group.algorithmVersion.takeIf { it > 0 } ?: "—"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Double.formatCurrent(): String =
    String.format(java.util.Locale.US, "%.2f", this).replace('.', ',')

@Composable
private fun ModuleDetailsCard(
    module: PanelModule,
    selection: SelectedApparatusSnapshot?,
    unitPrice: MoneyRange?,
    catalogUpdatedAt: String?,
    onSelectModel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = module.designation,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = module.phase?.color() ?: MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${module.moduleUnits} мод. · ${module.poles}P",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = module.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = module.detailParams(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            selection?.let {
                Text(
                    text = it.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Артикул ${it.article}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            )
            if (unitPrice != null) {
                DetailRow(
                    label = "Средняя цена",
                    value = "≈ ${formatEstimateRubles(unitPrice.typicalKopecks)}",
                    valueColor = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Диапазон ${formatEstimateRubles(unitPrice.minKopecks)}–${formatEstimateRubles(unitPrice.maxKopecks)}" +
                        catalogUpdatedAt?.let { " · цены на ${UiTextFormat.dateFromIso(it)}" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Цена для этой спецификации пока не определена",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onSelectModel,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (selection == null) "Выбрать модель" else "Изменить модель и цену")
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun PanelVisualizationLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PanelVisualizationEmpty() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Сначала добавьте комнаты и устройства, чтобы ВольтХом смог построить визуализацию щита.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

private fun ModuleType.shortTitle(): String {
    return when (this) {
        ModuleType.BREAKER -> "АВ"
        ModuleType.RCD -> "УЗО"
        ModuleType.RCBO -> "ДИФ"
        ModuleType.VOLTAGE_RELAY -> "РН"
        ModuleType.PHASE_CONTROL_RELAY -> "РФ"
        ModuleType.CURRENT_RELAY -> "РТ"
        ModuleType.MODULAR_CONTACTOR -> "КМ"
        ModuleType.SURGE_PROTECTION_DEVICE -> "УЗИП"
    }
}

private fun PanelModule.mainParam(): String {
    return nominalCurrent?.let { "${it}${UiTextFormat.NBSP}А" }
        ?: leakageCurrent?.let { "${it}${UiTextFormat.NBSP}мА" }
        .orEmpty()
}

private fun PanelModule.secondaryParam(): String {
    val parts = buildList {
        breakerCurve?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (nominalCurrent != null) {
            leakageCurrent?.let { add("${it}${UiTextFormat.NBSP}мА") }
        }
    }
    return parts.joinToString(" · ")
}

private fun PanelModule.detailParams(): String {
    val parts = buildList {
        nominalCurrent?.let { add("номинал ${it}${UiTextFormat.NBSP}А") }
        breakerCurve?.takeIf { it.isNotBlank() }?.let { add("характеристика $it") }
        leakageCurrent?.let { add("утечка ${it}${UiTextFormat.NBSP}мА") }
    }
    return parts.joinToString(" · ").ifBlank { "Параметры не указаны" }
}

private fun Phase.color(): Color = VhColors.phase(toUiPhase())

private fun Phase.displayLabel(): String {
    return when (this) {
        Phase.A -> "A"
        Phase.B -> "B"
        Phase.C -> "C"
        Phase.THREE_PHASE -> "3Ф"
    }
}

private fun Double.formatCableSection(): String {
    return if (this % 1.0 == 0.0) toInt().toString() else UiTextFormat.decimal(this)
}

private fun String.panelBrandShort(): String = when (trim().lowercase()) {
    "schneider electric" -> "SE"
    else -> trim()
}
