package ru.mugalimov.volthome.ui.screens.rooms

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.ui.components.device.DeviceParamsEditor
import ru.mugalimov.volthome.ui.components.VhTopBar
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsDraft
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsEditorState
import ru.mugalimov.volthome.ui.components.device.adapter.InMemoryDeviceParamsAdapter
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.onboarding.OnboardingRuntimeEntryPoint
import ru.mugalimov.volthome.ui.onboarding.hints.BaseHints
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.utilities.bringIntoViewOnFocus
import ru.mugalimov.volthome.ui.utilities.label
import ru.mugalimov.volthome.ui.viewmodel.RoomsAction
import ru.mugalimov.volthome.ui.viewmodel.RoomsViewModel
import java.util.Locale
import ru.mugalimov.volthome.ui.format.UiTextFormat
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRoomScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
    viewModel: RoomsViewModel = hiltViewModel(),
    formViewModel: AddRoomFormViewModel = hiltViewModel(),
    paywallHolder: AddRoomPaywallHolder = hiltViewModel()
) {
    val context = LocalContext.current
    val onboardingCoordinator = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OnboardingRuntimeEntryPoint::class.java
        ).onboardingCoordinator()
    }
    val defaultDevices by viewModel.defaultDevices.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val canUseExtendedEditor = LocalUserPlan.current.capabilities.extendedDeviceEditor

    val name by formViewModel.name.collectAsStateWithLifecycle()
    val selectedTypeName by formViewModel.selectedTypeName.collectAsStateWithLifecycle()
    val selectedType = RoomType.valueOf(selectedTypeName)
    val quantities by formViewModel.quantities.collectAsStateWithLifecycle()
    val presetInitialized by formViewModel.presetInitialized.collectAsStateWithLifecycle()
    val userInteracted by formViewModel.userInteracted.collectAsStateWithLifecycle()
    var pendingType by remember { mutableStateOf<RoomType?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val editorAdapter = remember(canUseExtendedEditor, paywallHolder) {
        InMemoryDeviceParamsAdapter<String>(
            isAllowed = canUseExtendedEditor,
            paywall = paywallHolder::onAdvancedEditorLocked
        )
    }
    val expandedKeys = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        val hint = BaseHints.ADD_ROOM_BUILD_ROOM
        onboardingCoordinator.tryShow(
            hintId = hint.hintId,
            screen = hint.screen,
            targetTag = hint.targetTag,
            title = hint.title,
            body = hint.body
        )
    }

    LaunchedEffect(defaultDevices, presetInitialized) {
        if (!presetInitialized && defaultDevices.isNotEmpty()) {
            formViewModel.initializePreset(presetFor(selectedType, defaultDevices))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.actions.collect { action ->
            when (action) {
                is RoomsAction.RoomCreated -> onCreated(action.roomId)
                is RoomsAction.UserMessage -> snackbar.showSnackbar(action.message)
                is RoomsAction.Error -> snackbar.showSnackbar(
                    action.throwable.localizedMessage ?: "Не удалось создать комнату"
                )
                is RoomsAction.DevicesAdded -> Unit
            }
        }
    }

    val selectedCount by remember(quantities) {
        derivedStateOf { quantities.values.sum() }
    }
    val selectedPower by remember(quantities, defaultDevices) {
        derivedStateOf {
            defaultDevices.sumOf { device ->
                device.power.toDouble() * (quantities[device.id.toString()] ?: 0)
            }
        }
    }
    val selectedDevices = remember(defaultDevices, quantities) {
        defaultDevices.sortedWith(
            compareByDescending<DefaultDevice> { (quantities[it.id.toString()] ?: 0) > 0 }
                .thenBy { it.name }
        )
    }

    fun requestBack() {
        if (userInteracted) showDiscardDialog = true else onBack()
    }
    BackHandler(onBack = ::requestBack)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            VhTopBar(
                title = "Новая комната",
                subtitle = "Выберите тип и оборудование",
                onBack = ::requestBack
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$selectedCount ${plural(selectedCount, "устройство", "устройства", "устройств")}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = fmtPower(selectedPower),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        enabled = selectedCount > 0 && !isBusy,
                        onClick = {
                            val requests = buildRequests(defaultDevices, quantities, editorAdapter, canUseExtendedEditor)
                            val expectedRows = quantities.count { it.value > 0 }
                            if (requests.size != expectedRows) {
                                scope.launch {
                                    snackbar.showSnackbar("Проверьте параметры выбранных устройств")
                                }
                                return@Button
                            }
                            viewModel.createRoomWithDevicesCustomized(
                        name = name.ifBlank { roomTypeShortLabel(selectedType) },
                                roomType = selectedType,
                                devices = requests
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isBusy) "Создаём…" else "Создать")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        formViewModel.setName(it.take(40))
                    },
                    label = { Text("Название комнаты") },
                    placeholder = { Text(roomTypeShortLabel(selectedType)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onboardingAnchor(
                            targetTag = OnboardingTargetTag.ADD_ROOM_NAME_FIELD,
                            screenId = OnboardingScreen.ADD_ROOM_SHEET
                        )
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Тип комнаты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    RoomTypeRow(
                        types = RoomType.entries,
                        selected = selectedType,
                        onSelect = { newType ->
                            if (newType != selectedType) pendingType = newType
                        }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("Устройства", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Выбранные позиции показаны первыми",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectedCount > 0) {
                        Text(
                            text = "Выбрано: $selectedCount",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            items(selectedDevices, key = { it.id }, contentType = { "device_picker_row" }) { device ->
                val key = device.id.toString()
                val qty = quantities[key] ?: 0
                val state = editorAdapter.state(key, device.seedDraft())
                DevicePickerRow(
                    device = device,
                    quantity = qty,
                    expanded = expandedKeys[key] == true,
                    state = state,
                    canUseExtendedEditor = canUseExtendedEditor,
                    scope = scope,
                    onToggle = { expandedKeys[key] = !(expandedKeys[key] ?: false) },
                    onQuantityChange = { newQuantity ->
                        formViewModel.setQuantities(quantities.toMutableMap().apply {
                            if (newQuantity <= 0) remove(key) else put(key, newQuantity.coerceAtMost(99))
                        })
                        formViewModel.markInteracted()
                    },
                    onEdited = formViewModel::markInteracted,
                    onLockedClick = editorAdapter::onLockedClick
                )
            }
        }
    }

    pendingType?.let { newType ->
        AlertDialog(
            onDismissRequest = { pendingType = null },
            title = { Text("Применить другой шаблон?") },
            text = { Text("Количество выбранных устройств будет заменено шаблоном «${roomTypeShortLabel(newType)}». Изменённые параметры устройств сохранятся.") },
            confirmButton = {
                TextButton(onClick = {
                    formViewModel.setTypeAndPreset(newType, presetFor(newType, defaultDevices))
                    pendingType = null
                }) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = { pendingType = null }) { Text("Отмена") } }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Закрыть без сохранения?") },
            text = { Text("Введённое название и выбранные устройства будут потеряны.") },
            confirmButton = { TextButton(onClick = onBack) { Text("Закрыть") } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Продолжить") } }
        )
    }
}

@Composable
private fun RoomTypeRow(types: List<RoomType>, selected: RoomType, onSelect: (RoomType) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type ->
            val selectedNow = type == selected
            Surface(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (selectedNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                color = if (selectedNow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            ) {
                Text(
                    text = roomTypeShortLabel(type),
                    modifier = Modifier.clickable { onSelect(type) }.padding(horizontal = 14.dp, vertical = 9.dp),
                    color = if (selectedNow) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DevicePickerRow(
    device: DefaultDevice,
    quantity: Int,
    expanded: Boolean,
    state: DeviceParamsEditorState<String>,
    canUseExtendedEditor: Boolean,
    scope: CoroutineScope,
    onToggle: () -> Unit,
    onQuantityChange: (Int) -> Unit,
    onEdited: () -> Unit,
    onLockedClick: () -> Unit
) {
    val context = LocalContext.current
    val selected = quantity > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.draft.name.ifBlank { device.name },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${state.draft.powerText}\u00A0Вт · ${state.draft.deviceType.label(context)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                QuantityStepper(quantity, onQuantityChange)
                IconButton(onClick = onToggle) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Скрыть параметры" else "Настроить"
                    )
                }
            }

            AnimatedVisibility(expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.padding(16.dp)) {
                        DeviceParamsEditor(
                            name = state.draft.name,
                            onNameChange = { onEdited(); state.onNameChange(it) },
                            nameError = state.errors.nameError,
                            powerText = state.draft.powerText,
                            onPowerTextChange = { onEdited(); state.onPowerTextChange(it) },
                            powerError = state.errors.powerError,
                            deviceType = state.draft.deviceType,
                            onDeviceTypeChange = { onEdited(); state.onDeviceTypeChange(it) },
                            powerFactorText = state.draft.powerFactorText,
                            onPowerFactorTextChange = { onEdited(); state.onPowerFactorTextChange(it) },
                            powerFactorError = state.errors.powerFactorError,
                            demandRatioText = state.draft.demandRatioText,
                            onDemandRatioTextChange = { onEdited(); state.onDemandRatioTextChange(it) },
                            demandRatioError = state.errors.demandRatioError,
                            voltageType = state.draft.voltageType,
                            onVoltageTypeChange = { onEdited(); state.onVoltageTypeChange(it) },
                            hasMotor = state.draft.hasMotor,
                            onHasMotorChange = { onEdited(); state.onHasMotorChange(it) },
                            requiresDedicatedCircuit = state.draft.requiresDedicatedCircuit,
                            onRequiresDedicatedCircuitChange = { onEdited(); state.onRequiresDedicatedCircuitChange(it) },
                            requiresSocketConnection = state.draft.requiresSocketConnection,
                            onRequiresSocketConnectionChange = { onEdited(); state.onRequiresSocketConnectionChange(it) },
                            locked = !canUseExtendedEditor,
                            onLockedClick = onLockedClick,
                            scope = scope
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuantityStepper(value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(0)) }, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.Remove, contentDescription = "Уменьшить", modifier = Modifier.size(18.dp))
        }
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            Text(value.toString(), fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = { onValueChange((value + 1).coerceAtMost(99)) }, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = "Увеличить", modifier = Modifier.size(18.dp))
        }
    }
}

private fun buildRequests(
    defaults: List<DefaultDevice>,
    quantities: Map<String, Int>,
    editorAdapter: InMemoryDeviceParamsAdapter<String>,
    isAllowed: Boolean
): List<DeviceCreateRequest> = defaults.mapNotNull { default ->
    val key = default.id.toString()
    val count = quantities[key] ?: 0
    if (count <= 0) return@mapNotNull null
    val normalized = editorAdapter.peekDraftNormalized(key, default.seedDraft())
    val watts = normalized.powerText.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
    DeviceCreateRequest(
        title = normalized.name.ifBlank { default.name },
        type = if (isAllowed) normalized.deviceType else default.deviceType,
        count = count,
        ratedPowerW = watts,
        powerFactor = if (isAllowed) normalized.powerFactorText.toDoubleOrNull() ?: return@mapNotNull null else default.powerFactor,
        demandRatio = if (isAllowed) normalized.demandRatioText.toDoubleOrNull() ?: return@mapNotNull null else default.demandRatio,
        voltage = if (isAllowed) voltageFor(normalized.voltageType, default) else default.voltage,
        hasMotor = if (isAllowed) normalized.hasMotor else default.hasMotor,
        requiresDedicatedCircuit = if (isAllowed) normalized.requiresDedicatedCircuit else default.requiresDedicatedCircuit,
        requiresSocketConnection = if (isAllowed) normalized.requiresSocketConnection else default.requiresSocketConnection
    )
}

private fun DefaultDevice.seedDraft() = DeviceParamsDraft(
    name = name,
    powerText = power.toString(),
    deviceType = deviceType,
    powerFactorText = powerFactor.format(2),
    demandRatioText = demandRatio.format(2),
    voltageType = voltage.type,
    hasMotor = hasMotor,
    requiresDedicatedCircuit = requiresDedicatedCircuit,
    requiresSocketConnection = requiresSocketConnection
)

private fun voltageFor(type: VoltageType, fallback: DefaultDevice) = when (type) {
    VoltageType.AC_1PHASE -> Voltage(230, type)
    VoltageType.AC_3PHASE -> Voltage(400, type)
    VoltageType.DC -> fallback.voltage
}

private fun presetFor(type: RoomType, all: List<DefaultDevice>): Map<String, Int> {
    val result = mutableMapOf<String, Int>()
    fun add(deviceType: DeviceType, count: Int) {
        all.firstOrNull { it.deviceType == deviceType }?.let { result[it.id.toString()] = count }
    }
    when (type) {
        RoomType.STANDARD -> { add(DeviceType.LIGHTING, 1); add(DeviceType.SOCKET, 1) }
        RoomType.BATHROOM -> { add(DeviceType.LIGHTING, 1); add(DeviceType.SOCKET, 1); add(DeviceType.HEAVY_DUTY, 1) }
        RoomType.KITCHEN -> { add(DeviceType.LIGHTING, 1); add(DeviceType.SOCKET, 2); add(DeviceType.HEAVY_DUTY, 1) }
        RoomType.OUTDOOR -> add(DeviceType.SOCKET, 1)
    }
    return result
}

private fun roomTypeShortLabel(type: RoomType) = when (type) {
    RoomType.STANDARD -> "Стандартная"
    RoomType.BATHROOM -> "Ванная"
    RoomType.KITCHEN -> "Кухня"
    RoomType.OUTDOOR -> "Улица"
}

private fun fmtPower(value: Double) = UiTextFormat.power(value)
private fun Double.format(digits: Int) = String.format(Locale.US, "%.${digits}f", this)

private fun plural(value: Int, one: String, few: String, many: String): String {
    val mod100 = value % 100
    val mod10 = value % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

@HiltViewModel
class AddRoomPaywallHolder @Inject constructor(private val paywallBus: PaywallBus) : ViewModel() {
    fun onAdvancedEditorLocked() = paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR)
}

/** Сохраняет основной черновик формы даже при пересоздании Activity. */
@HiltViewModel
class AddRoomFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    val name: StateFlow<String> = savedStateHandle.getStateFlow(KEY_NAME, "")
    val selectedTypeName: StateFlow<String> = savedStateHandle.getStateFlow(KEY_TYPE, RoomType.STANDARD.name)
    val quantities: StateFlow<Map<String, Int>> = savedStateHandle.getStateFlow(KEY_QUANTITIES, emptyMap())
    val presetInitialized: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_PRESET_INITIALIZED, false)
    val userInteracted: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_INTERACTED, false)

    fun setName(value: String) {
        savedStateHandle[KEY_NAME] = value
        markInteracted()
    }

    fun setQuantities(value: Map<String, Int>) {
        savedStateHandle[KEY_QUANTITIES] = HashMap(value)
    }

    fun initializePreset(value: Map<String, Int>) {
        if (presetInitialized.value) return
        setQuantities(value)
        savedStateHandle[KEY_PRESET_INITIALIZED] = true
    }

    fun setTypeAndPreset(type: RoomType, value: Map<String, Int>) {
        savedStateHandle[KEY_TYPE] = type.name
        setQuantities(value)
        savedStateHandle[KEY_PRESET_INITIALIZED] = true
        markInteracted()
    }

    fun markInteracted() {
        savedStateHandle[KEY_INTERACTED] = true
    }

    private companion object {
        const val KEY_NAME = "add_room_name"
        const val KEY_TYPE = "add_room_type"
        const val KEY_QUANTITIES = "add_room_quantities"
        const val KEY_PRESET_INITIALIZED = "add_room_preset_initialized"
        const val KEY_INTERACTED = "add_room_interacted"
    }
}
