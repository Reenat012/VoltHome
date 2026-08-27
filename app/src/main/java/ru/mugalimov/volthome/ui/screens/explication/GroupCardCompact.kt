package ru.mugalimov.volthome.ui.screens.explication

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceCalcBreakdown
import ru.mugalimov.volthome.domain.model.DeviceSpecUi
import ru.mugalimov.volthome.domain.model.cable.CableLineCalculation
import ru.mugalimov.volthome.domain.model.cable.CableCalculationStatus
import ru.mugalimov.volthome.ui.format.ExplicationNumberFormat as F
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetPayload
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCardCompact(
    group: CircuitGroup,
    isManualMode: Boolean,

    // ✅ Только для одной карточки на экране включаем anchor
    // на первом device chip, чтобы подсказка указывала в конкретную цель.
    anchorFirstDeviceChip: Boolean = false,

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
    cableCalculation: CableLineCalculation? = null,
    onCableClick: (CircuitGroup) -> Unit,
    onGroupPowerClick: (CircuitGroup) -> Unit,
    onGroupCurrentClick: (CircuitGroup) -> Unit,
    onOpenInfoSheet: (InfoSheetPayload) -> Unit
) {
    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetDevice = remember { mutableStateOf<DeviceSpecUi?>(null) }
    val caps = LocalUserPlan.current.capabilities

    val cs = MaterialTheme.colorScheme
    val bgTrack = cs.surfaceContainer
    val textSecondary = cs.onSurfaceVariant

    val calculatedPowerW = group.devices
        .sumOf { it.power.toDouble() * it.demandRatio }
        .roundToInt()

    val TAG_GROUP = "EXP_GROUP"

    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .clickable { onOpenInfoSheet(buildGroupHeaderPayload(group)) }
                .padding(horizontal = 14.dp, vertical = 12.dp)
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

            Spacer(Modifier.height(6.dp))

            if (group.manualDeviationCodes.isNotEmpty()) {
                Text(
                    text = "Ручная настройка · ${group.manualDeviationCodes.size} ${deviationWord(group.manualDeviationCodes.size)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.tertiary
                )
                Spacer(Modifier.height(8.dp))
            }

            // Четыре основных параметра образуют одну компактную строку.
            // Подробности и вторичные характеристики доступны по нажатию.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GroupMetricCell(
                    label = "Автомат",
                    value = "${group.breakerType}${group.circuitBreaker}",
                    modifier = Modifier.weight(1f)
                ) {
                    onOpenInfoSheet(GroupDecisionPayloadFactory.breaker(group))
                }
                GroupMetricCell(
                    label = "Ток расч.",
                    value = "${F.a(group.nominalCurrent, decimals = 2)}\u00A0А",
                    modifier = Modifier.weight(1f)
                ) {
                    onGroupCurrentClick(group)
                }
                GroupMetricCell(
                    label = when (cableCalculation?.status) {
                        CableCalculationStatus.PASSED -> "Кабель ✓"
                        CableCalculationStatus.WARNING,
                        CableCalculationStatus.FAILED -> "Кабель !"
                        CableCalculationStatus.PRELIMINARY,
                        null -> "Кабель"
                    },
                    value = cableCalculation?.cable?.compactLabel
                        ?: "${group.cableSection}\u00A0мм²",
                    modifier = Modifier.weight(1f),
                    emphasized = true,
                    locked = !caps.cableLineCalculation
                ) {
                    onCableClick(group)
                }
                GroupMetricCell(
                    label = "УЗО",
                    value = if (group.rcdRequired) "${group.rcdSpec?.leakageCurrentMa ?: group.rcdCurrent}\u00A0мА" else "—",
                    modifier = Modifier.weight(1f)
                ) {
                    onOpenInfoSheet(GroupDecisionPayloadFactory.rcd(group))
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

                    // ✅ Anchor вешаем только на первый device chip
                    // первой карточки, которую выберет экран.
                    anchorFirstVisibleDevice = anchorFirstDeviceChip,
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
                    text = "${F.kwFromW(calculatedPowerW, decimals = 2)}\u00A0кВт",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary,
                    modifier = Modifier.clickable { onGroupPowerClick(group) }
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

private fun deviationWord(value: Int): String {
    val mod100 = value % 100
    val mod10 = value % 10
    return when {
        mod100 in 11..14 -> "отклонений"
        mod10 == 1 -> "отклонение"
        mod10 in 2..4 -> "отклонения"
        else -> "отклонений"
    }
}

@Composable
private fun GroupMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    locked: Boolean = false,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (emphasized) cs.primaryContainer.copy(alpha = 0.48f) else cs.surfaceContainerHigh
    val outline = if (emphasized) cs.primary.copy(alpha = 0.72f) else cs.outlineVariant.copy(alpha = 0.60f)
    val iconTint = if (emphasized) cs.primary else cs.onSurfaceVariant
    val textPrimary = if (emphasized) cs.primary else cs.onSurface

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = bg,
        border = BorderStroke(1.dp, outline)
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = iconTint,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                if (emphasized) {
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = if (locked) Icons.Outlined.Lock else Icons.Outlined.Edit,
                        contentDescription = if (locked) "Доступно в PRO" else "Редактировать кабель",
                        tint = iconTint,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Payload builders
// ──────────────────────────────────────────────────────────────────────────────

private fun buildGroupHeaderPayload(group: CircuitGroup): InfoSheetPayload =
    GroupDecisionPayloadFactory.group(group)
