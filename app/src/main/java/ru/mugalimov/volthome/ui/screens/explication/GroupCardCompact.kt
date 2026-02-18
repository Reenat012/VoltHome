package ru.mugalimov.volthome.ui.screens.explication

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceCalcBreakdown
import ru.mugalimov.volthome.domain.model.DeviceSpecUi
import ru.mugalimov.volthome.ui.format.ExplicationNumberFormat as F
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcDetailsState
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetPayload
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCardCompact(
    group: CircuitGroup,
    isManualMode: Boolean,

    // Старое: включает "move-mode" (панель целей)
    onDeviceLongPress: (deviceId: Long, fromGroupId: Long) -> Unit,

    // Новое: drag-цепочка событий
    onDeviceDragStart: (deviceId: Long, fromGroupId: Long, itemStartRoot: Offset, pointerStartRoot: Offset) -> Unit,
    onDeviceDragMove: (pointerRoot: Offset) -> Unit,
    onDeviceDragEnd: () -> Unit,
    onDeviceDragCancel: () -> Unit,

    onEdit: (() -> Unit)? = null,
    onDeviceClick: (Long) -> Unit,
    selectedDeviceBreakdown: DeviceCalcBreakdown?,
    onGroupPowerClick: (CircuitGroup) -> Unit,
    onGroupCurrentClick: (CircuitGroup) -> Unit,
    onOpenInfoSheet: (InfoSheetPayload) -> Unit
) {
    // ✅ Коммит 6:
// expanded должен жить по стабильному идентификатору группы.
// groupId — единственный корректный ключ для сохранения состояния карточки.
    val expanded = rememberSaveable(group.groupId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetDevice = remember { mutableStateOf<DeviceSpecUi?>(null) }
    val caps = LocalUserPlan.current.capabilities

    val cs = MaterialTheme.colorScheme
    val divider = cs.outlineVariant.copy(alpha = 0.45f)
    val bgTrack = cs.surfaceContainer
    val textSecondary = cs.onSurfaceVariant

    val calculatedPowerW = group.devices
        .sumOf { it.power.toDouble() * it.demandRatio }
        .roundToInt()

    val TAG_GROUP = "EXP_GROUP"

    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .clickable {
                    val newValue = !expanded.value
                    Log.d(TAG_GROUP, "toggleExpand groupId=${group.groupId} num=${group.groupNumber} new=$newValue")
                    expanded.value = newValue
                }
                .padding(16.dp)
        ) {
            // ── Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Группа ${group.groupNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = group.roomName.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    // IconButton сам обрабатывает клик
                    onClick = { onOpenInfoSheet(buildGroupHeaderPayload(group)) }
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParamBadge(
                    icon = Icons.Outlined.ElectricalServices,
                    text = "${group.breakerType}${group.circuitBreaker}"
                ) {
                    onOpenInfoSheet(buildBreakerPayload(group))
                }

                ParamBadge(
                    icon = Icons.Outlined.Bolt,
                    text = "${F.kwFromW(calculatedPowerW, decimals = 2)} кВт"
                ) {
                    onGroupPowerClick(group)
                }

                ParamBadge(
                    icon = Icons.Outlined.ElectricBolt,
                    text = "${F.a(group.nominalCurrent, decimals = 2)} А"
                ) {
                    onGroupCurrentClick(group)
                }
            }

            // ── Devices chips + device sheet
            if (group.devices.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                DeviceChips(
                    devices = group.devices,
                    onDeviceClick = { id ->
                        Log.d(TAG_GROUP, "deviceClick groupId=${group.groupId} deviceId=$id manual=$isManualMode")
                        val real = group.devices.firstOrNull { it.id == id }
                        if (real != null) {
                            sheetDevice.value = DeviceSpecUi(
                                id = real.id,
                                name = real.name,
                                power = real.power,
                                voltage = real.voltage.value,
                                demandRatio = real.demandRatio,
                                powerFactor = real.powerFactor,
                                deviceType = real.deviceType,
                                hasMotor = real.hasMotor,
                                requiresDedicatedCircuit = real.requiresDedicatedCircuit,
                                requiresSocketConnection = real.requiresSocketConnection
                            )
                            scope.launch { sheetState.show() }
                        }
                        onDeviceClick(id)
                    },

                    onDeviceLongPress = { id ->
                        Log.d(TAG_GROUP, "deviceLongPress groupId=${group.groupId} deviceId=$id manual=$isManualMode")
                        if (isManualMode) onDeviceLongPress(id, group.groupId)
                    },

                    onDeviceDragStart = { deviceId, itemStartRoot, pointerStartRoot ->
                        Log.d(
                            TAG_GROUP,
                            "deviceDragStart groupId=${group.groupId} deviceId=$deviceId manual=$isManualMode " +
                                    "itemStartRoot=$itemStartRoot pointerStartRoot=$pointerStartRoot"
                        )
                        if (!isManualMode) return@DeviceChips
                        onDeviceDragStart(deviceId, group.groupId, itemStartRoot, pointerStartRoot)
                    },
                    onDeviceDragMove = { pointerRoot ->
                        Log.v(TAG_GROUP, "deviceDragMove groupId=${group.groupId} manual=$isManualMode pointerRoot=$pointerRoot")
                        if (!isManualMode) return@DeviceChips
                        onDeviceDragMove(pointerRoot)
                    },
                    onDeviceDragEnd = {
                        Log.d(TAG_GROUP, "deviceDragEnd groupId=${group.groupId} manual=$isManualMode")
                        if (!isManualMode) return@DeviceChips
                        onDeviceDragEnd()
                    },
                    onDeviceDragCancel = {
                        Log.w(TAG_GROUP, "deviceDragCancel groupId=${group.groupId} manual=$isManualMode")
                        if (!isManualMode) return@DeviceChips
                        onDeviceDragCancel()
                    },

                    enableLongPress = isManualMode,
                    maxVisible = 3,
                    // ✅ Коммит 6:
                    // Если DeviceChips хранит состояние (например, анимации/локальный state),
                    // то ключ должен быть groupId, а не groupNumber.
                    groupKey = group.groupId
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Load progress
            val load =
                (if (group.circuitBreaker > 0) group.nominalCurrent / group.circuitBreaker else 0.0)
                    .coerceAtLeast(0.0)

            val barColor = when {
                load > 0.8 -> cs.error
                load > 0.6 -> cs.tertiary
                else -> cs.primary
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Загрузка",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary
                )
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { load.coerceAtMost(1.0).toFloat() },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = barColor,
                    trackColor = bgTrack
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${(load * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary
                )
            }

            // ── Expanded details
            AnimatedVisibility(visible = expanded.value) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = divider, thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))

                    GroupParameterRow("Тип группы", group.groupType.toString())
                    GroupParameterRow("Сечение кабеля", "${group.cableSection} мм²")
                    GroupParameterRow("Фаза", "${group.phase}")
                    if (group.rcdRequired) {
                        GroupParameterRow("УЗО", "${group.rcdCurrent} мА")
                    }
                }
            }
        }
    }

    if (sheetDevice.value != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    sheetDevice.value = null
                }
            },
            sheetState = sheetState
        ) {
            DeviceSpecSheet(
                device = sheetDevice.value!!,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        sheetDevice.value = null
                    }
                },
                sheetState = sheetState,
                breakdown = selectedDeviceBreakdown,
                showProfessionalSections = caps.professionalReportSections,
            )
        }
    }
}

@Composable
private fun ParamBadge(icon: ImageVector, text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = cs.surfaceContainerHigh
    val outline = cs.outlineVariant.copy(alpha = 0.60f)
    val iconTint = cs.onSurfaceVariant
    val textPrimary = cs.onSurface

    Surface(
        shape = MaterialTheme.shapes.large,
        color = bg,
        border = BorderStroke(1.dp, outline)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = textPrimary
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Payload builders
// ──────────────────────────────────────────────────────────────────────────────

private fun buildGroupHeaderPayload(group: CircuitGroup): InfoSheetPayload =
    InfoSheetPayload(
        title = "Карточка группы",
        sheetType = InfoSheetType.REFERENCE,
        bullets = listOf(
            "Здесь параметры группы: автомат, мощность, ток и состав устройств.",
            "Блок помогает быстро оценить загрузку и необходимость перераспределения."
        ),
        calcDetailsState = CalcDetailsState.HIDDEN
    )

private fun buildBreakerPayload(group: CircuitGroup): InfoSheetPayload {
    val curveChar = group.breakerType.firstOrNull()?.uppercaseChar() ?: 'C'
    val title = "Тип автомата: $curveChar${group.circuitBreaker}"
    val common =
        "Формат «$curveChar${group.circuitBreaker}»: буква — кривая мгновенного отключения, число — номинал, А."
    val body = when (curveChar) {
        'B' -> "Кривая B ≈ 3–5×In. Для активных нагрузок и длинных линий."
        'C' -> "Кривая C ≈ 5–10×In. Дефолт для розеточных/смешанных групп."
        'D' -> "Кривая D ≈ 10–20×In. Для больших пусков (двигатели, насосы, сварка)."
        else -> "Обычно используют B, C или D."
    }

    return InfoSheetPayload(
        title = title,
        sheetType = InfoSheetType.REFERENCE,
        interpretation = "$common\n\n$body",
        calcDetailsState = CalcDetailsState.HIDDEN
    )
}