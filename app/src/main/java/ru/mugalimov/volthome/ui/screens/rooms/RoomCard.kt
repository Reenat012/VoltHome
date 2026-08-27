package ru.mugalimov.volthome.ui.screens.rooms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.domain.model.RoomType

@Composable
fun RoomCard(
    room: RoomWithDevicesPreviewUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onDelete: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val needsRcd = room.roomType in setOf(RoomType.BATHROOM, RoomType.KITCHEN, RoomType.OUTDOOR)
    val t = VhColors.tokens
    val cardBg = t.surfaceAlt

    ElevatedCard(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoomTypeAvatar(
                type = room.roomType,
                modifier = Modifier
                    .size(44.dp)
                    .padding(end = 12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = room.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = t.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = roomTypeLabel(room.roomType),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (onDelete != null) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Действия с комнатой", tint = t.textSecondary)
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Удалить комнату") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        confirmDelete = true
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoftChip("${room.devicesCount} ${plural(room.devicesCount, "устройство", "устройства", "устройств")}")
                    if (needsRcd) UzoChip()
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${room.name}»?") },
            text = {
                Text(
                    "Комната и ${room.devicesCount} ${plural(room.devicesCount, "устройство", "устройства", "устройств")} будут удалены из проекта."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete?.invoke()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
}

/* ---------------- helpers ---------------- */

@Composable
private fun RoomTypeAvatar(type: RoomType, modifier: Modifier = Modifier) {
    val icon = when (type) {
        RoomType.STANDARD -> Icons.Rounded.Home
        RoomType.BATHROOM -> Icons.Rounded.Bathtub
        RoomType.KITCHEN -> Icons.Rounded.Kitchen
        RoomType.OUTDOOR -> Icons.Rounded.Park
    }

    val t = VhColors.tokens
    val bg: Color = t.surface
    val fg: Color = t.textSecondary

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, t.divider)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = fg)
        }
    }
}

@Composable
private fun SoftChip(text: String) {
    val t = VhColors.tokens
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = t.surface,
        border = BorderStroke(1.dp, t.divider)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = t.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UzoChip() {
    val t = VhColors.tokens
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = t.primarySurface,
        border = BorderStroke(1.dp, t.primary)
    ) {
        Text(
            text = "Особая зона · УЗО 30 мА",
            style = MaterialTheme.typography.labelMedium,
            color = t.textPrimary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun roomTypeLabel(type: RoomType): String = when (type) {
    RoomType.STANDARD -> "Стандартная"
    RoomType.BATHROOM -> "Ванная"
    RoomType.KITCHEN -> "Кухня"
    RoomType.OUTDOOR -> "Улица"
}

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
