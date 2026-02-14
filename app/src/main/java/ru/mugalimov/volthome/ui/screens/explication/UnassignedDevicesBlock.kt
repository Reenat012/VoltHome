package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Device

/**
 * Контейнер "Нераспределённые устройства" (manual).
 *
 * Цель UI:
 * - выглядеть как карточка группы (структура/плотность/бейджи),
 * - но иметь явный акцент: "требуется вмешательство".
 *
 * Функционал:
 * - устройства отображаются чипами
 * - long-press -> drag (через DeviceChips / detectDragGesturesAfterLongPress)
 */
@Composable
fun UnassignedDevicesBlock(
    devices: List<Device>,
    onAutoAssignClick: () -> Unit,

    // ✅ Drag (manual-only)
    onDeviceDragStart: (deviceId: Long, itemStartRoot: Offset, pointerStartRoot: Offset) -> Unit,
    onDeviceDragMove: (pointerRoot: Offset) -> Unit,
    onDeviceDragEnd: () -> Unit,
    onDeviceDragCancel: () -> Unit,

    // (опционально) клик по устройству
    onDeviceClick: (Long) -> Unit = {},

    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val hasUnassigned = devices.isNotEmpty()

    // Акцент:
    // - если есть устройства -> тревожный (error) + подчёркиваем необходимость действия
    // - если пусто -> нейтральный
    val accent = if (hasUnassigned) cs.error else cs.outlineVariant
    val accentBg = if (hasUnassigned) cs.errorContainer else cs.surfaceContainerHigh
    val accentText = if (hasUnassigned) cs.onErrorContainer else cs.onSurface

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        color = cs.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ── Header (как у группы: иконка состояния + заголовок + доп.строка)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (hasUnassigned) Icons.Outlined.ReportProblem else Icons.Outlined.Info,
                    contentDescription = null,
                    tint = accent
                )
                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Нераспределённые устройства",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                    Text(
                        text = if (hasUnassigned) "Требуется вмешательство" else "Всё распределено",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasUnassigned) accent else cs.onSurfaceVariant
                    )
                }

                // Пока no-op: можно потом открыть InfoSheet
                IconButton(onClick = { /* no-op */ }) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = cs.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Бейджи (как у группы: статус + количество)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentBg,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (hasUnassigned) "Вне групп" else "Пусто",
                            style = MaterialTheme.typography.labelLarge,
                            color = accentText
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Кол-во: ${devices.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Подсказка (деловая, не простыня)
            Text(
                text = if (hasUnassigned) {
                    "Эти устройства не относятся ни к одной группе. Перенеси их вручную (long-press) или восстанови автоматически."
                } else {
                    "Если устройство не относится ни к одной группе, оно появится здесь."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // ── Устройства: чипы + drag
            if (!hasUnassigned) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Нет нераспределённых устройств.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            } else {
                DeviceChips(
                    devices = devices.sortedBy { it.name.trim().lowercase() },
                    onDeviceClick = onDeviceClick,

                    // Старый long-press контракт тут не нужен — drag сам включает перенос через screen/VM
                    onDeviceLongPress = null,

                    // ✅ Drag-цепочка
                    onDeviceDragStart = { deviceId, itemStartRoot, pointerStartRoot ->
                        onDeviceDragStart(deviceId, itemStartRoot, pointerStartRoot)
                    },
                    onDeviceDragMove = { pointerRoot ->
                        onDeviceDragMove(pointerRoot)
                    },
                    onDeviceDragEnd = { onDeviceDragEnd() },
                    onDeviceDragCancel = { onDeviceDragCancel() },

                    enableLongPress = true,
                    // В unassigned логично показывать больше, чем в группе
                    maxVisible = 6,
                    groupKey = "unassigned"
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── CTA (главное действие)
            FilledTonalButton(
                onClick = onAutoAssignClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasUnassigned
            ) {
                Icon(Icons.Outlined.AutoFixHigh, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Распределить автоматически")
            }
        }
    }
}